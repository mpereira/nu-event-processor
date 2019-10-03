(ns nu-event-processor.rules
  (:require [nu-event-processor.operation :refer [operation-type]]))

(def operation-rule-violation-types
  {:account [:account-already-initialized]
   :transaction [:account-not-initialized
                 :card-not-active
                 :insufficient-limit
                 :high-frequency-small-interval
                 :doubled-transaction]})

(defmulti operation-violates-rule?
  (fn [_ condition-violation-type operation]
    (let [operation-type* (operation-type operation)]
      [operation-type* condition-violation-type])))

(defmethod operation-violates-rule?
  [:account :account-already-initialized]
  [state _ _]
  (:account @state))

(defmethod operation-violates-rule?
  [:transaction :account-not-initialized]
  [state _ _]
  (not (:account @state)))

(defmethod operation-violates-rule?
  [:transaction :card-not-active]
  [state _ _]
  (let [state* @state]
    (and (:account state*)
         (not (get-in state* [:account :active-card])))))

(defmethod operation-violates-rule?
  [:transaction :insufficient-limit]
  [state _ transaction]
  (if-let [available-limit (get-in @state [:account :available-limit])]
    (let [transaction-amount (get-in transaction [:transaction :amount])]
      (> transaction-amount available-limit))
    false))

(defmethod operation-violates-rule?
  [:transaction :high-frequency-small-interval]
  [state _ transaction]
  (:rate-limited? (:rate-limiter @state)))

(defmethod operation-violates-rule?
  [:transaction :doubled-transaction]
  [state _ _]
  (:duplicate-detected? (:duplicate-preventer @state)))

(defmulti operation-rule-violation-error-message
  (fn [_ rule-violation-type operation]
    (let [operation-type* (operation-type operation)]
      [operation-type* rule-violation-type])))

(defmethod operation-rule-violation-error-message
  [:account :account-already-initialized]
  [state _ operation]
  "Account was already initialized")

(defmethod operation-rule-violation-error-message
  [:transaction :account-not-initialized]
  [state _ _]
  "Account was not yet initialized")

(defmethod operation-rule-violation-error-message
  [:transaction :card-not-active]
  [state _ _]
  "The card is not active")

(defmethod operation-rule-violation-error-message
  [:transaction :insufficient-limit]
  [state _ _]
  "The available limit is insufficient")

(defmethod operation-rule-violation-error-message
  [:transaction :high-frequency-small-interval]
  [state _ _]
  "There has been more than 3 transactions in the last 2 minutes")

(defmethod operation-rule-violation-error-message
  [:transaction :doubled-transaction]
  [state _ _]
  "There has been a similar transaction in the last 2 minutes")

(defn operation-rule-violation-errors [state operation]
  (let [operation-type (operation-type operation)]
    (reduce (fn [violations violation-type]
              (if (operation-violates-rule? state violation-type operation)
                (conj violations
                      (operation-rule-violation-error-message state
                                                              violation-type
                                                              operation))
                violations))
            []
            (get operation-rule-violation-types operation-type))))
