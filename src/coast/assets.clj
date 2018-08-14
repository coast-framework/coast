(ns coast.assets
  (:require [asset-minifier.core :refer [minify-js minify-css]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [coast.utils :as utils])
  (:import (java.security MessageDigest)
           (java.io File)))

(defn md5 [s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn ext [s]
  (when (string? s)
    (second (re-find #"\.(.*)$" s))))

(defn hrefs [k v]
  (mapv #(str "/" k "/" %) v))

(defn paths [k v]
  (mapv #(str "resources/public/" k "/" %) v))

(defn copy-file [source-path dest-path]
  (io/copy (io/file source-path) (io/file dest-path)))

(defn assets-dir []
  (.mkdirs (File. "resources/public/assets"))
  "resources/public/assets")

(defn minify-bundle [bundle v]
  (let [ext (ext bundle)
        name (second (re-find #"^(.*)\..*" bundle))
        tmp (str (utils/uuid) "." ext)
        _ (condp = ext
            "css" (minify-css (paths ext v) tmp)
            "js" (minify-js (paths ext v) tmp)
            nil)
        checksum (-> tmp slurp md5)
        _ (copy-file tmp (str (assets-dir) "/" name "-" checksum "." ext))
        _ (io/delete-file tmp)]
    (str "/assets/" name "-" checksum "." ext)))

(defn bundles [minify? m]
  {::bundles (->> (map (fn [[k v]] {k (if minify?
                                       [(minify-bundle k v)]
                                       (hrefs (ext k) v))})
                       m)
                  (apply merge))})

(defn build [m]
  (->> (map (fn [[k v]] {k [(minify-bundle k v)]}) m)
       (apply merge)))

(defn pprint-write [filename val]
  (with-open [w (io/writer filename)]
    (binding [*out* w]
      (pprint/write val))))

(defn bundle [coast-env bundle-name]
  (if (= "prod" coast-env)
    (-> (io/resource "assets.minified.edn")
        (slurp)
        (edn/read-string)
        (get bundle-name))
    (let [m (-> (io/resource "assets.edn")
                (slurp)
                (edn/read-string))]
      (get (->> (map (fn [[k v]] {k (hrefs (ext k) v)}) m)
                (apply merge))
           bundle-name))))

(defn -main []
  (let [m (-> (io/resource "assets.edn")
              (slurp)
              (edn/read-string))]
    (doall
      (pprint-write "resources/assets.minified.edn" (build m)))))
