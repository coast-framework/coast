(ns {{project}}.views.{{table}}
  (:require [{{project}}.components :as c]
            [coast.core :as coast]))

(defn {{singular}} [m]
  (let [{:keys [{{column_string}}]} m]
    [:tr
     {{td_col_string}}
     [:td
      (coast/link-to "Edit" ["/{{table}}/:id/edit" m])]
     [:td
      (coast/link-to "Delete" [:delete "/{{table}}/:id" m])]
     [:td
      (coast/link-to "Show" ["/{{table}}/:id" m])]]))

(defn index [request]
  (let [{:keys [{{table}}]} request]
    [:div
      [:table
       [:thead
        [:tr
         {{th_col_string}}
         [:th]
         [:th]
         [:th]]]
       [:tbody
         (for [m {{table}}]
           ({{singular}} m))]]
      [:div
       (coast/link-to "New {{singular}}" ["/{{table}}/fresh"])]]))

(defn show [request]
 (let [{:keys [{{singular}}]} request
       {:keys [{{column_string}}]} {{singular}}]
   [:div
     {{div_col_string}}
     [:div
       (coast/link-to "Delete" [:delete "/{{table}}/:id" {{singular}}])]
     [:div
       (coast/link-to "Back" ["/{{table}}"])]]))

(defn fresh [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (coast/form-for [:post "/{{table}}"]
        {{form_col_string}}
        [:div
          [:input {:type "submit" :value "Save"}]])
      [:div
       (coast/link-to "Back" ["/{{table}}"])]]))

(defn edit [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (coast/form-for [:put "/{{table}}/:id" {{singular}}]
        {{form_col_string}}
        [:div
         [:input {:type "submit" :value "Save"}]])
      [:div
       (coast/link-to "Back" ["/{{table}}"])]]))
