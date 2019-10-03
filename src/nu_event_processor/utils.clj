(ns nu-event-processor.utils
  (:require [clj-time.format :as f]
            [clojure.java.io :as io])
  (:import java.io.BufferedReader
           java.util.Properties))

(defn date-time-string->date-time [s]
  (f/parse (f/formatters :date-time) s))

(defn jar-path
  "Returns the full path of the JAR file in which this function is invoked."
  [& [ns]]
  (-> (or ns (class *ns*))
      (.getProtectionDomain)
      (.getCodeSource)
      (.getLocation)
      (.toURI)
      (.getPath)))

(defn properties
  "Returns the properties for a given x or namespace."
  [x]
  (when-let [pom-properties (io/resource (str "META-INF/maven"
                                              "/" (or (namespace x) (name x))
                                              "/" (name x)
                                              "/" "pom.properties"))]
    (with-open [stream (io/input-stream pom-properties)]
      (let [properties (doto (Properties.) (.load stream))]
        {:artifact-id (.getProperty properties "artifactId")
         :group-id (.getProperty properties "groupId")
         :revision (.getProperty properties "revision")
         :version (.getProperty properties "version")}))))
