(ns repl
  (:require [nrepl.server]))

(let [port (or (some->> (slurp ".nrepl-port")
                        (re-find #"^\d+$")
                        (Integer.))
               7888)]
  (nrepl.server/start-server :port port)
  (println "Started nREPL on port" port))
