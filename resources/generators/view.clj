(ns __project.views.__table
  (:require [coast.alpha :as coast]))

(defn table-row [m]
  (let [{:keys [__columns]} m
        edit (coast/url ["/__table/:id/edit" m])
        delete (coast/url ["/__table/:id" m])
        show (coast/url ["/__table/:id" m])]
    [:tr
      __td-columns
      [:td
        [:a {:href edit} "Edit"]]
      [:td
        [:a {:href delete} "Delete"]]
      [:td
        [:a {:href show} "Show"]]]))

(defn index [request]
  (let [{:keys [__table]} request]
    [:div
      [:table
        [:thead
          [:tr
            __th-columns]]
        [:tbody
          (for [m __table]
            (table-row m))]]
      [:div
        [:a {:href "/__table/fresh"} "New __singular"]]]))

(defn show [request]
  (let [{:keys [__singular]} request
        {:keys [__columns]} __singular
        delete-href (coast/url [:delete "/__table/:id" __singular])]
    [:div
      __div-columns
      [:div
       [:a {:href delete-href} "Delete"]]
      [:div
        [:a {:href "/__table"} "Back"]]]))

(defn fresh [request]
  (let [{:keys [__singular]} request
        {:keys [__columns]} __singular]
    [:div
     (coast/form-for [:post "/__table"]
       __form-columns
       [:input {:type "submit" :value "Create"}])]))

(defn edit [request]
  (let [{:keys [__singular]} request
        {:keys [__columns]} __singular]
    [:div
     (coast/form-for [:put "/__table/:id" __singular]
       __form-columns
       [:input {:type "submit" :value "Save"}])]))
