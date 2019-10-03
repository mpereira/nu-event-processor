(ns nu-event-processor.core-test
  (:require [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.test :refer :all]
            [nu-event-processor.core :refer :all]))

(def test-cases
  [{:description "No limit available for last transaction"
    :input-stream [{:account {:active-card true
                              :available-limit 100}}
                   {:transaction {:merchant "Burger King"
                                  :amount 20
                                  :time "2019-02-13T10:00:00.000Z"}}
                   {:transaction {:merchant "Habbib's"
                                  :amount 90
                                  :time "2019-02-13T11:00:00.000Z"}}]
    :expected-output-stream [{:account {:active-card true
                                        :available-limit 100}
                              :violations []}
                             {:account {:active-card true
                                        :available-limit 80}
                              :violations []}
                             {:account {:active-card true
                                        :available-limit 80}
                              :violations ["The available limit is insufficient"]}]}
   {:description "Account already initialized"
    :input-stream [{:account {:active-card true
                              :available-limit 100}}
                   {:account {:active-card true
                              :available-limit 350}}]
    :expected-output-stream [{:account {:active-card true
                                        :available-limit 100}
                              :violations []}
                             {:account {:active-card true
                                        :available-limit 100}
                              :violations ["Account was already initialized"]}]}
   {:description "One transaction with available limit"
    :input-stream [{:account {:active-card true
                              :available-limit 100}}
                   {:transaction {:merchant "Burger King"
                                  :amount 20
                                  :time "2019-02-13T10:00:00.000Z"}}]
    :expected-output-stream [{:account {:active-card true
                                        :available-limit 100}
                              :violations []}
                             {:account {:active-card true
                                        :available-limit 80}
                              :violations []}]}
   {:description "One transaction with no available limit"
    :input-stream [{:account {:active-card true
                              :available-limit 80}}
                   {:transaction {:merchant "Burger King"
                                  :amount 90
                                  :time "2019-02-13T10:00:00.000Z"}}]
    :expected-output-stream [{:account {:active-card true
                                        :available-limit 80}
                              :violations []}
                             {:account {:active-card true
                                        :available-limit 80}
                              :violations ["The available limit is insufficient"]}]}
   {:description "Account not initialized"
    :input-stream [{:transaction {:merchant "Burger King"
                                  :amount 90
                                  :time "2019-02-13T10:00:00.000Z"}}]
    :expected-output-stream [{:account nil
                              :violations ["Account was not yet initialized"]}]}
   {:description "Card not active"
    :input-stream [{:account {:active-card false
                              :available-limit 100}}
                   {:transaction {:merchant "Burger King"
                                  :amount 20
                                  :time "2019-02-13T10:00:00.000Z"}}]
    :expected-output-stream [{:account {:active-card false
                                        :available-limit 100}
                              :violations []}
                             {:account {:active-card false
                                        :available-limit 100}
                              :violations ["The card is not active"]}]}
   {:description "Rate limiting and detecting duplicate transactions"
    :input-stream [{:account {:active-card true
                              :available-limit 1000}}
                   ;; 3 transactions between 10:00-10:01 should go through.
                   {:transaction {:merchant "Burger King"
                                  :amount 30
                                  :time "2019-02-13T10:00:00.000Z"}}
                   {:transaction {:merchant "McDonald's"
                                  :amount 20
                                  :time "2019-02-13T10:00:30.000Z"}}
                   {:transaction {:merchant "Bob's"
                                  :amount 15
                                  :time "2019-02-13T10:00:59.000Z"}}
                   ;; 2 transactions between 10:01-10:02 should go through. The
                   ;; rate limiter will be running down on capacity after
                   ;; handling events at double its rate and will start rate
                   ;; limiting after processing these.
                   {:transaction {:merchant "Cinema"
                                  :amount 30
                                  :time "2019-02-13T10:01:00.000Z"}}
                   {:transaction {:merchant "McDonald's"
                                  :amount 20
                                  :time "2019-02-13T10:01:30.000Z"}}
                   {:transaction {:merchant "McDonald's"
                                  :amount 20
                                  :time "2019-02-13T10:01:59.000Z"}}
                   ;; 3 transactions from the following 4 transactions between
                   ;; 10:02-10:03 should be rate limited.
                   {:transaction {:merchant "Bob's"
                                  :amount 15
                                  :time "2019-02-13T10:02:00.000Z"}}
                   {:transaction {:merchant "C&A"
                                  :amount 100
                                  :time "2019-02-13T10:02:30.000Z"}}]
    :expected-output-stream
    [;; Account is initialized
     {:account {:active-card true :available-limit 1000}
      :violations []}
     ;; The first 4 transactions go through between 10:00-10:01 without
     ;; triggering the rate limiter. This is a possibility in 'Token Bucket'
     ;; rate limiters, which is more concerned about maintaining a rate close to
     ;; the desired one based on the rate limiter capacity and time interval
     ;; given. For more details please check the
     ;; `nu-event-processor.rate-limiter/rate-limit` docstring.
     {:account {:active-card true :available-limit 970}
      :violations []}
     {:account {:active-card true :available-limit 950}
      :violations []}
     {:account {:active-card true :available-limit 935}
      :violations []}
     {:account {:active-card true :available-limit 905}
      :violations []}
     ;; The 5th transaction triggers the rate limiter, after draining its
     ;; capacity on the first 4 transaction events.
     {:account {:active-card true :available-limit 905}
      :violations
      ["There has been a similar transaction in the last 2 minutes"]}
     ;; The 6th transaction triggers the duplicate preventer in addition to the
     ;; rate limiter.
     {:account {:active-card true :available-limit 905}
      :violations
      ["There has been more than 3 transactions in the last 2 minutes"
       "There has been a similar transaction in the last 2 minutes"]}
     ;; The 7th transaction triggers the duplicate preventer in addition to the
     ;; rate limiter. Transaction events are still coming above the rate limiter
     ;; rate, so unless the rate lowers, transaction events will continue being
     ;; rate limited.
     {:account {:active-card true :available-limit 905}
      :violations
      ["There has been a similar transaction in the last 2 minutes"]}
     ;; Same.
     {:account {:active-card true :available-limit 905}
      :violations
      ["There has been more than 3 transactions in the last 2 minutes"]}]}])

(deftest end-to-end-test
  (doseq [{:keys [input-stream expected-output-stream description]} test-cases]
    (let [input (s/join \newline (map json/generate-string input-stream))
          output (with-out-str
                   (with-in-str input
                     (-main)))
          output-stream (map #(json/parse-string % true) (s/split output #"\n"))]
      (is (= expected-output-stream output-stream)
          description))))
