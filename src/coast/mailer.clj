(ns coast.mailer
  (:require [hiccup.core :as h]
            [coast.jobs :as jobs]))

(defn mail-fn [var opts]
  (fn [m]
    (let [email (select-keys m [:to :from :subject :html :text])
          args (->> (assoc email :html (h/html (:html email)))
                    (merge opts))]
      (jobs/queue var args))))

(defmacro mail [f opts]
  `(mail-fn ~(resolve f) ~opts))
