(ns nu-event-processor.processor
  (:require [nu-event-processor.duplicate-preventer :refer [prevent-duplicate]]
            [nu-event-processor.operation :refer [operation-type]]
            [nu-event-processor.rate-limiter :refer [rate-limit]]
            [nu-event-processor.rules :refer [operation-rule-violation-errors]]
            [nu-event-processor.validations :refer [operation-validation-errors]]))

(defn initialize-account! [system {:keys [account]}]
  (swap! system assoc :account account))

(defn authorize-transaction! [system {{:keys [amount]} :transaction}]
  (swap! system update-in [:account :available-limit] - amount))

(defmulti process-operation!
  (fn [_ operation]
    (operation-type operation)))

(defmethod process-operation!
  :account
  [system {:keys [account] :as operation}]
  (let [validation-errors (operation-validation-errors operation)]
    (if (seq validation-errors)
      (swap! system update :output-stream conj (assoc {:account (:account @system)}
                                                      :violations
                                                      validation-errors))
      (let [rule-violation-errors (operation-rule-violation-errors system operation)]
        (when-not (seq rule-violation-errors)
          (initialize-account! system operation))
        (swap! system update :output-stream conj (assoc {:account (:account @system)}
                                                        :violations
                                                        rule-violation-errors)))))
  system)

(defmethod process-operation!
  :transaction
  [system operation]
  (swap! system update :rate-limiter (fn [& _]
                                       (rate-limit
                                        (:rate-limiter @system)
                                        operation)))
  (swap! system update :duplicate-preventer (fn [& _]
                                              (prevent-duplicate
                                               (:duplicate-preventer @system)
                                               operation)))
  (let [validation-errors (operation-validation-errors operation)]
    (if (seq validation-errors)
      (swap! system update :output-stream conj (assoc {:account (:account @system)}
                                                      :violations
                                                      validation-errors))
      (let [rule-violation-errors (operation-rule-violation-errors system operation)]
        (when-not (seq rule-violation-errors)
          (authorize-transaction! system operation))
        (swap! system update :output-stream conj (assoc {:account (:account @system)}
                                                        :violations
                                                        rule-violation-errors)))))
  system)

(defn process-operations!
  "This functions isn't currently used, but is here to demonstrate that
  `process-operation!` is a reducing function."
  [system operations]
  (reduce process-operation! system operations))
