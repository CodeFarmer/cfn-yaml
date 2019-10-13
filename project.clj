(defproject cfn-yaml "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-1.0"
            :url "https://www.eclipse.org/legal/epl-1.0/"}

  :dependencies [[org.clojure/clojure  "1.10.0"]
                 [clj-commons/clj-yaml "0.6.0"]]
      
  :main ^:skip-aot aerosol-lint.core
  
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :test {:resource-paths ["test-resources"]}})
