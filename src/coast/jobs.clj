(ns coast.jobs
  (:require [coast.time :as time]
            [coast.db :refer [defq]]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:refer-clojure :exclude [run update]))

(defq insert "sql/jobs.sql")
(defq update "sql/jobs.sql")
(defq queued "sql/jobs.sql")

(defn qualified-symbol [val]
  (when (qualified-ident? val)
    (if (qualified-keyword? val)
      (symbol (namespace val) (name val))
      val)))

(defn queue-job [f args scheduled-at]
  (insert {:function (str (qualified-symbol f))
           :args (str args)
           :finished-at nil
           :scheduled-at scheduled-at}))

(defn queue-at [at f & args]
  (queue-job f args at))

(defn queue [f & args]
  (queue-job f args nil))

(defn resolve* [s]
  (let [[q-ns q-name] (->> (string/split s #"/")
                           (map symbol))]
    (load (str (string/replace q-ns #"\." "/")))
    (ns-resolve q-ns q-name)))

(defn run [job]
  (let [{:keys [function args]} job
        f (resolve* function)
        parsed-args (edn/read-string args)
        parsed-args (if (sequential? parsed-args)
                     parsed-args
                     [parsed-args])]
    (when (not (nil? f))
      (if (empty? parsed-args)
          (f)
          (apply f parsed-args))
      (update (assoc job :finished-at (time/now))))))

(defn run-jobs [jobs]
  (doseq [j jobs]
    (run j)))

(defn start []
  (-> (queued)
      (run-jobs))
  (Thread/sleep 10000)
  (start))

(defn -main [& args]
  (println "The jobs table is currently being polled")
  (start))
