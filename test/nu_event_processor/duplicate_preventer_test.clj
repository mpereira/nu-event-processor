(ns nu-event-processor.duplicate-preventer-test
  (:require [clojure.test :refer :all]
            [nu-event-processor.duplicate-preventer :refer :all]))

(def test-cases
  [{:description (str "Two similar events at the same time are "
                      "detected as duplicate")
    :duplicate-preventer (make-duplicate-preventer
                          :key-fn first
                          :time-fn last
                          :per-interval-s 10)
    :events [[0 "2019-02-13T10:00:00.000Z"]
             [0 "2019-02-13T10:00:00.000Z"]]
    :expected-duplicates [false
                          true]}
   {:description (str "Two similar events within the defined interval are "
                      "detected as duplicate")
    :duplicate-preventer (make-duplicate-preventer
                          :key-fn first
                          :time-fn last
                          :per-interval-s 10)
    :events [[0 "2019-02-13T10:00:00.000Z"]
             [0 "2019-02-13T10:00:09.000Z"]]
    :expected-duplicates [false
                          true]}
   {:description (str "Two similar events outside the defined interval are "
                      "not detected as duplicate")
    :duplicate-preventer (make-duplicate-preventer
                          :key-fn first
                          :time-fn last
                          :per-interval-s 10)
    :events [[0 "2019-02-13T10:00:00.000Z"]
             [0 "2019-02-13T10:00:11.000Z"]]
    :expected-duplicates [false
                          false]}
   {:description (str "Two different events inside the defined interval are "
                      "not detected as duplicate")
    :duplicate-preventer (make-duplicate-preventer
                          :key-fn first
                          :time-fn last
                          :per-interval-s 10)
    :events [[0 "2019-02-13T10:00:00.000Z"]
             [1 "2019-02-13T10:00:09.000Z"]]
    :expected-duplicates [false
                          false]}
   {:description (str "Two different events inside the defined interval are "
                      "not detected as duplicate")
    :duplicate-preventer (make-duplicate-preventer
                          :key-fn first
                          :time-fn last
                          :per-interval-s 10)
    :events [[0 "2019-02-13T10:00:00.000Z"]
             [1 "2019-02-13T10:00:01.000Z"]
             [2 "2019-02-13T10:00:02.000Z"]
             [3 "2019-02-13T10:00:03.000Z"]
             [0 "2019-02-13T10:00:04.000Z"]
             [3 "2019-02-13T10:00:05.000Z"]
             [2 "2019-02-13T10:00:06.000Z"]
             [1 "2019-02-13T10:00:07.000Z"]
             [0 "2019-02-13T10:00:15.000Z"]
             [9 "2019-02-13T10:00:40.000Z"]]
    :expected-duplicates [;; Events 0 to 3 are all happening for the first time.
                          false
                          false
                          false
                          false
                          ;; Event 0 happens again after 4 seconds and triggers
                          ;; the duplicate preventer.
                          true
                          ;; Event 3 happens again after 2 seconds and triggers
                          ;; the duplicate preventer.
                          true
                          ;; Event 2 happens again after 4 seconds and triggers
                          ;; the duplicate preventer.
                          true
                          ;; Event 1 happens again after 6 seconds and triggers
                          ;; the duplicate preventer.
                          true
                          ;; Event 0 happens again after 11 seconds and doesn't
                          ;; trigger the duplicate preventer.
                          false
                          ;; Event 9 happens for the first time and doesn't
                          ;; trigger the duplicate preventer.
                          false]}])

(deftest prevent-duplicate-test
  (doseq [{:keys [duplicate-preventer events expected-duplicates description]}
          test-cases]
    (let [duplicate-preventer-states (next (reductions prevent-duplicate
                                                       duplicate-preventer
                                                       events))]
      (is (= expected-duplicates
             (map :duplicate-detected? duplicate-preventer-states))
          description))))
