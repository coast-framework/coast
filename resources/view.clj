(ns {{project}}.views.{{table}}
  (:require [{{project}}.components :as c]
            [coast.core :as coast]))

(defn {{singular}} [m]
  (let [{:keys [{{column_string}}]} m]
    [:tr{% for col in columns %}
     [:td {{col}}{% endfor %}]
     [:td
      (coast/link-to "Edit {{singular}}" ["/{{table}}/:id/edit" m])]
     [:td
      (coast/link-to "Show {{singular}}" ["/{{table}}/:id" m])]]))

(defn index [request]
  (let [{:keys [{{table}}]} request]
    [:div
      [:table
       [:thead
        [:tr{% for col in columns %}
         [:th "{{col}}"]{% endfor %}]]
       [:tbody
         (for [m {{table}}]
           ({{singular}} m))]]
      [:div
       (coast/link-to "New {{singular}}" ["/{{table}}/fresh"])]]))

(defn show [request]
 (let [{:keys [{{singular}}]} request
       {:keys [{{column_string}}]} {{singular}}]
   [:div{% for col in columns %}
     [:div {{col}}]{% endfor %}
     [:div
      (coast/link-to "Back" ["/{{table}}"])]]))

(defn fresh [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (coast/form-for [:post "/{{table}}"]{% for col in form_columns %}
        [:div
         [:label "{{col}}"]
         [:input {:type "text" :name "{{col}}" :value {{col}}{% endfor %}}]]
        [:div
          [:input {:type "submit" :value "Save"}]])
      [:div
       (coast/link-to "Back" ["/{{table}}"])]]))

(defn edit [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (coast/form-for [:put "/{{table}}/:id" {{singular}}]{% for col in form_columns %}
        [:div
         [:label "{{col}}"]
         [:input {:type "text" :name "{{col}}" :value {{col}}{% endfor %}}]]
        [:div
         [:input {:type "submit" :value "Save"}]])
      [:div
       (coast/link-to "Back" ["/{{table}}"])]]))
