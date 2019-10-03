(defproject nu-event-processor "0.1.0-SNAPSHOT"
  :description "nu-event-processor is a tool for processing account events"
  :url "https://github.com/mpereira/nu-event-processor"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [cheshire "5.9.0"]
                 [clj-time "0.15.2"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main ^:skip-aot nu-event-processor.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["test/resources"]
                   :dependencies [[pjstadig/humane-test-output "0.9.0"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]
                   :plugins [[lein-ancient "0.6.15"]
                             [lein-bikeshed "0.5.2"]
                             [lein-cljfmt "0.6.4"]]}})
