(ns coast.logger
  (:require [clojure.string :as string]
            [coast.time :as time]
            [coast.utils :as utils]))


(defn log-str [request response start-time]
  (let [ms (- (time/now-millis) start-time)
        uri (:uri request)
        status (:status response)
        route (:route response)
        method (some-> request :request-method name string/upper-case)
        headers (some->> (:headers response)
                         (utils/map-keys string/lower-case))
        content-type (get headers "content-type")
        timestamp (-> (time/now) (time/zoned) (time/fmt "yyyy-MM-dd HH:mm:ss xx"))]
    (->> [timestamp method (str "\"" uri "\"")
          route status content-type
          ms "ms"]
         (filter some?)
         (map str)
         (filter #(not (string/blank? %)))
         (string/join " "))))


(defn log [request response start-time]
  (println (log-str request response start-time)))
