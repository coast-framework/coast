(defproject coast "0.1.0-SNAPSHOT"
  :description "An easy to use clojure web framework"
  :url "https://coastonclojure.com"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [potemkin "0.4.4"]
                 [http-kit "2.2.0"]
                 [trail "1.10.0"]
                 [bunyan "0.1.1"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-defaults "0.2.3"]
                 [ring/ring-devel "1.5.0"]
                 [org.postgresql/postgresql "42.1.4"]
                 [org.clojure/java.jdbc "0.7.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ragtime "0.7.2"]
                 [oksql "1.0.0"]
                 [selmer "1.11.1"]
                 [inflections "0.13.0"]
                 [prone "1.1.4"]
                 [com.jakemccrary/reload "0.1.0"]])
