(ns nu-event-processor.operation)

(def operation-types {:account :account
                      :transaction :transaction})

(defn operation-type [operation]
  (first (keys operation)))

(defn transaction-time [operation]
  (get-in operation [:transaction :time]))
