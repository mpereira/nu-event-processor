(ns nu-event-processor.system
  (:require [clj-time.core :as t]
            [nu-event-processor.duplicate-preventer
             :refer [make-duplicate-preventer]]
            [nu-event-processor.operation :refer [transaction-time]]
            [nu-event-processor.rate-limiter :refer [make-rate-limiter]]))

(defn make-system []
  {:account nil
   :output-stream []
   :rate-limiter (make-rate-limiter
                  :capacity 3
                  :per-interval-s (t/in-seconds (t/minutes 2))
                  :time-fn transaction-time)
   :duplicate-preventer (make-duplicate-preventer
                         :key-fn (comp (juxt :amount :merchant)
                                       :transaction)
                         :time-fn transaction-time
                         :per-interval-s (t/in-seconds (t/minutes 2)))})
