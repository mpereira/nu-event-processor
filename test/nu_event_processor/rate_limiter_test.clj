(ns nu-event-processor.rate-limiter-test
  (:require [clojure.test :refer :all]
            [nu-event-processor.rate-limiter :refer :all]))

(def test-cases
  [{:description (str "2 events at the same time rate-limited at 2 events/s "
                      "shouldn't trigger rate-limiting")
    :rate-limiter (make-rate-limiter :capacity 2
                                     :per-interval-s 1)
    :events ["2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:00.000Z"]
    :expected-rate-limits [false
                           false]}
   {:description (str "3 events at the same time rate-limited at 2 events/s "
                      "triggers rate-limiting on the 3rd event")
    :rate-limiter (make-rate-limiter :capacity 2
                                     :per-interval-s 1)
    :events ["2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:00.000Z"]
    :expected-rate-limits [false
                           false
                           true]}
   {:description (str "4 events at a rate of 2 events/s rate-limited at 2 events/s "
                      "shouldn't trigger rate-limiting")
    :rate-limiter (make-rate-limiter :capacity 2
                                     :per-interval-s 1)
    :events ["2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:00.500Z"
             "2019-02-13T10:00:01.000Z"
             "2019-02-13T10:00:01.500Z"]
    :expected-rate-limits [false
                           false
                           false
                           false]}
   {:description (str "4 events at a rate of 4 events/s rate-limited at 2 events/s "
                      "triggers rate-limiting on the 3rd and 4th events")
    :rate-limiter (make-rate-limiter :capacity 2
                                     :per-interval-s 1)
    :events ["2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:00.250Z"
             "2019-02-13T10:00:00.500Z"
             "2019-02-13T10:00:00.750Z"]
    :expected-rate-limits [false
                           false
                           true
                           true]}
   {:description (str "10 events at a rate quadruple that of the rate limiter "
                      "followed by a brief pause "
                      "followed by 6 events at same rate that of the rate limiter "
                      "triggers rate-limiting initially and eventually recovers")
    :rate-limiter (make-rate-limiter :capacity 3
                                     :per-interval-s 120)
    :events [;; Starts with 12 events / 120 seconds (4x the rate limiter rate).
             "2019-02-13T10:00:00.000Z"
             "2019-02-13T10:00:10.000Z"
             "2019-02-13T10:00:20.000Z"
             "2019-02-13T10:00:30.000Z"
             "2019-02-13T10:00:40.000Z"
             "2019-02-13T10:00:50.000Z"
             "2019-02-13T10:01:00.000Z"
             "2019-02-13T10:01:10.000Z"
             "2019-02-13T10:01:20.000Z"
             "2019-02-13T10:01:30.000Z"
             ;; Pauses for 20 seconds, then re-starts with 3 events / 120
             ;; seconds (same rate as the rate limiter: 1 event / 40 seconds).
             "2019-02-13T10:01:50.000Z"
             "2019-02-13T10:02:30.000Z"
             "2019-02-13T10:03:10.000Z"
             "2019-02-13T10:03:50.000Z"
             "2019-02-13T10:04:20.000Z"
             "2019-02-13T10:04:50.000Z"]
    :expected-rate-limits [;; The rate limiter starts with capacity 3, so the
                           ;; first 3 events are not rate limited even though
                           ;; their rate is over the rate limiter's.
                           false
                           false
                           false
                           ;; The rate exausts the rate limiter capacity.
                           true
                           ;; One unit of capacity is recovered here. At this
                           ;; rate (4x the rate limiter's) 1 in 4 events will
                           ;; not be rate limited. This is one of them.
                           false
                           ;; Capacity is re-exausted right afterwards.
                           true
                           true
                           true
                           ;; Another one of those 1 in 4 events that will not
                           ;; be rate limited.
                           false
                           ;; The following is rate limited as expected.
                           true
                           ;; This is where the event rate become the same as
                           ;; the rate limiter. The first event is still rate
                           ;; limited because the capacity still needs to
                           ;; recover a bit more.
                           true
                           ;; The event rate continues at the same rate as the
                           ;; rate limiter itself which allows the rate limiter
                           ;; to eventually recover all capacity, so no more
                           ;; events are limited.
                           false
                           false
                           false
                           false
                           false]}])

(deftest rate-limit-test
  (doseq [{:keys [rate-limiter events expected-rate-limits description]}
          test-cases]
    (let [rate-limiter-states (next (reductions rate-limit
                                                rate-limiter
                                                events))]
      (is (= expected-rate-limits
             (map :rate-limited? rate-limiter-states))
          description))))
