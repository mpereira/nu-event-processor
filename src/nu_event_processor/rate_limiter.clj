(ns nu-event-processor.rate-limiter
  (:require [clj-time.coerce :as c]
            [nu-event-processor.utils :refer [date-time-string->date-time]]))

(defn make-rate-limiter [& {:keys [capacity per-interval-s time-fn]
                            :or {time-fn identity}}]
  {:capacity capacity
   :tokens capacity
   :refill-rate (/ capacity per-interval-s)
   :per-interval-s per-interval-s
   :time-fn time-fn
   :last-event-time-s nil})

(defn rate-limit
  "A purely functional implementation of https://en.wikipedia.org/wiki/Token_bucket.

  This strategy is arguably more correct than a 'Fixed Window' strategy where
  event counts are maintained in time interval buckets of arbitrary resolutions,
  given that that strategy allows up to '2 * `max-interval` - 1' events in a
  given interval (where N is the supposed maximum number of events allowed per
  interval) if they are timed right.

  A 'Sliding Window' strategy would be the most correct in terms of making it
  impossible for more than `capacity` events in any given time interval of size
  `per-interval-s`, at the cost of having both time and space complexity of
  'O(`capacity`)'.

  With the 'Token Bucket' strategy it is possible that more than `capacity`
  events in a given time interval of size `per-interval-s`. Events are rate
  limited based on the actual rate of events. As long as the rate isn't above
  the desired average (`capacity` / `per-interval-s`) for long enough, events
  won't be rate-limited. This implementation has both time and space complexity
  of 'O(1)' and is commonly used in high-performance rate-limiters."
  [{:keys [time-fn capacity tokens refill-rate per-interval-s last-event-time-s]
    :as rate-limiter}
   event]
  (let [now-s (-> event
                  (time-fn)
                  (date-time-string->date-time)
                  (c/to-long)
                  (quot 1000))
        time-since-last-event-s (if last-event-time-s
                                  (- now-s last-event-time-s)
                                  0)
        ;; Refill tokens based on the the amount of time passed, capped at
        ;; `capacity`.
        refilled-tokens (min capacity
                             (+ tokens
                                (* time-since-last-event-s
                                   refill-rate)))
        hit-rate-limit? (< refilled-tokens 1)]
    (assoc rate-limiter
           :rate-limited? hit-rate-limit?
           :last-event event
           :last-event-time-s now-s
           :tokens (if hit-rate-limit?
                     refilled-tokens
                     (dec refilled-tokens)))))
