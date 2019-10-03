(ns nu-event-processor.duplicate-preventer
  (:require [clj-time.coerce :as c]
            [nu-event-processor.utils :refer [date-time-string->date-time]]))

(defn make-duplicate-preventer [& {:keys [key-fn time-fn per-interval-s]
                                   :or {key-fn identity
                                        time-fn identity}}]
  {:events-by-key {}
   :key-fn key-fn
   :time-fn time-fn
   :per-interval-s per-interval-s})

(defn prevent-duplicate
  [{:keys [events-by-key key-fn time-fn per-interval-s] :as duplicate-preventer}
   event]
  (if-let [key* (key-fn event)]
    (let [now-s (-> event
                    (time-fn)
                    (date-time-string->date-time)
                    (c/to-long)
                    (quot 1000))
          last-seen-at-s (get events-by-key key*)
          has-duplicate-within-interval? (and last-seen-at-s
                                              (> per-interval-s
                                                 (- now-s last-seen-at-s)))]
      (-> duplicate-preventer
          (assoc :duplicate-detected? (boolean has-duplicate-within-interval?))
          (update :events-by-key assoc key* now-s)))
    duplicate-preventer))
