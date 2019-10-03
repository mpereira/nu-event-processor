(ns nu-event-processor.core
  (:require [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.tools.cli :refer [parse-opts]]
            [nu-event-processor.processor :as processor]
            [nu-event-processor.system :refer [make-system]]
            [nu-event-processor.utils :refer [jar-path properties]])
  (:import com.fasterxml.jackson.core.JsonParseException
           java.io.BufferedReader)
  (:gen-class))

(defonce program-properties (properties 'nu-event-processor))

(def program-name (:artifact-id program-properties))

(def program-description
  (str program-name " is a tool for processing account events"))

(def program-version (:version program-properties))

(def program-revision (:revision program-properties))

(def program-short-revision (subs program-revision 0 8))

(def program-version-and-revision
  (str program-version " (" program-short-revision ")"))

(def program-jar-path (jar-path))

(def program-command (str "java -jar " program-jar-path))

(defn run
  "Instantiates the system, reads, parses and processes JSON-encoded operations
  written to STDIN."
  [options]
  (let [system (atom (make-system))]
    (doseq [line (line-seq (BufferedReader. *in*))]
      (try
        (let [operation (json/parse-string line true)]
          (processor/process-operation! system operation)
          (println (json/generate-string (last (:output-stream @system)))))
        (catch JsonParseException e
          (binding [*out* *err*]
            (println "Error parsing line" (str "'" line "'") "as JSON:"
                     (.getMessage e))))))
    {:stdout nil
     :return-code 0}))

(def cli-options
  [["-v" "--version" "Show version"
    :id :version]
   ["-h" "--help" "Show help"
    :id :help]])

(defn usage-message [summary & [{:keys [show-preamble?]
                                 :or {show-preamble? true}}]]
  (let [preamble
        (when show-preamble?
          (->> [(str program-name " " program-version-and-revision)
                ""
                program-description
                ""]
               (s/join \newline)))]
    (->> [preamble
          "Usage:"
          (str "  " program-command " < $INPUT_FILE [OPTIONS]")
          ""
          "Options:"
          summary]
         (s/join \newline))))

(defn error-message [{:keys [raw-args errors] :as parsed-opts} subcommand]
  (str "The following errors occurred while parsing your command:"
       " "
       "`" program-name " " (s/join " " raw-args) "`"
       "\n\n"
       (s/join \newline errors)
       "\n\n"
       "Run `" program-command " --help" "` for more information"))

(defn valid-command? [{:keys [arguments summary options] :as parsed-opts}]
  (empty? arguments))

(defn dispatch-command
  [{:keys [arguments summary options raw-args] :as parsed-opts}]
  (cond
    (or (:help options)
        (contains? (set arguments) "help")) {:stdout (usage-message summary)
                                             :return-code 0}
    (:version options) {:stdout program-version-and-revision
                        :return-code 0}
    (valid-command? parsed-opts) (run options)
    :else {:stdout
           (str "Invalid command: "
                "`" program-name (when raw-args (apply str " " raw-args)) "`"
                \newline
                (usage-message summary {:show-preamble? false}))
           :return-code 1}))

(defn -main
  "Entry-point to the nu-event-processor."
  [& args]
  (let [{:keys [errors] :as parsed-opts} (assoc (parse-opts args cli-options)
                                                :raw-args args)]
    (if errors
      (println (error-message parsed-opts))
      (let [{:keys [stdout return-code]} (dispatch-command parsed-opts)]
        (when stdout
          (println stdout))
        (when return-code
          ;; (System/exit return-code)
          )))))
