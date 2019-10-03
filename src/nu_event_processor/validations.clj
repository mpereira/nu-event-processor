(ns nu-event-processor.validations
  (:require [nu-event-processor.operation :refer [operation-type
                                                  operation-types]]))

(def operation-validation-types [:exactly-one-key
                                 :valid-operation-type])

(defmulti operation-violates-validation?
  (fn [condition-violation-type _] condition-violation-type))

(defmethod operation-violates-validation?
  :exactly-one-key
  [_ operation]
  (not (= 1 (count (keys operation)))))

(defmethod operation-violates-validation?
  :valid-operation-type
  [_ operation]
  (not (contains? operation-types (operation-type operation))))

(defmulti operation-validation-error-message
  (fn [condition-violation-type _] condition-violation-type))

(defmethod operation-validation-error-message
  :exactly-one-key
  [_ operation]
  (let [keys* (keys operation)]
    (str "Should have exactly 1 key, has " (count keys*) ": " keys*)))

(defmethod operation-validation-error-message
  :valid-operation-type
  [_ operation]
  (str "Operation type invalid: '" (operation-type operation) "'. "
       "Available: " (keys operation-types)))

(defn operation-validation-errors [operation]
  (reduce (fn [errors violation-type]
            (if (operation-violates-validation? violation-type operation)
              (conj errors
                    (operation-validation-error-message violation-type
                                                        operation))
              errors))
          []
          operation-validation-types))
