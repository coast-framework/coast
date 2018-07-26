(ns repl
  (:require [nrepl.server]))

(let [port (or (some-> (first *command-line-args*)
                       (java.lang.Long/parseLong))
               7888)]
  (nrepl.server/start-server :port port)
  (println "Started nREPL on port" port))
