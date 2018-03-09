(ns cider
  (:require [clojure.tools.nrepl.server :refer [start-server]]
            [cider.nrepl :refer [cider-nrepl-handler]]))

(let [port (or (some-> (first *command-line-args*)
                       (java.lang.Long/parseLong))
               7888)]
  (start-server :port port :handler cider-nrepl-handler)
  (println "Started nREPL on port" port)
  (println "To connect cider to this nrepl, run 'M-x cider-connect'"))
