(defproject wind-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "www.iainschmitt.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"] [environ "1.2.0"] [twitter-api "1.8.0"] [overtone/at-at "1.2.0"] [clj-time "0.15.2"] [org.clojure/math.numeric-tower "0.0.4"]]
  :main wind-bot.core
  :plugins [[lein-environ "1.2.0"] [jarohen/chime "0.3.2"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
