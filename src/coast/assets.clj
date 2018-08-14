(ns coast.assets
  (:require [asset-minifier.core :refer [minify-js minify-css]]
            [clojure.java.io :as io]
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
