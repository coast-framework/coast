(ns coast.worker
  (:require [coast.jobs :as jobs]))

(defn run-jobs [jobs]
  (doseq [j jobs]
    (jobs/run j)))

(defn start []
  (-> (jobs/queued)
      (run-jobs))
  (Thread/sleep 10000)
  (start))

(defn -main [& args]
  (println "The jobs table is currently being polled")
  (start))
