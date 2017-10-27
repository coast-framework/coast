(ns {{project}}.views.{{table}}
  (:require [{{project}}.components :as c]
            [coast.core :as coast]))

(defn row [{{singular}}]
  [:tr{% for col in columns %}
   [:td (:{{col}} m)]{% endfor %}
   [:td
    (coast/link-to "Edit" :{{table}}/edit {{singular}})]
   [:td
    (coast/link-to "Show" :{{table}} {{singular}})]])

(defn index [request]
  (let [{:keys [{{table}}]} request]
    [:table
     (map {{singular}} {{table}})]
    [:div
     (coast/link-to routes :{{table}}/new {}
       "New {{singular}}")]))

(defn show [{{singular}}]
 {% for col in columns %}
 [:div (:{{col}} {{singular}}{% endfor %})]
 [:div
  (coast/link-to "Back" :{{table}})])

(defn new- [{:keys [{{singular}} error]}]
  [:div
    error
    (coast/form-for [:{{table}}] [{{singular}}]{% for col in form_columns %}
      [:div
        (coast/field {:type "text" :name "{{col}}" :value (:{{col}} {{singular}})}){% endfor %}]
      [:div
        [:input {:type "submit" :value "Save"}]]
      [:div
        (coast/link-to "Back" [:{{table}}])])])

(defn edit [{:keys [{{singular}} error]}]
  [:div
    error
    (coast/form-for [:{{table}}] [{{singular}}]{% for col in form_columns %}
      [:div
       (coast/field {:type "text" :name "{{col}}" :value (:{{col}} {{singular}})}){% endfor %}]
      [:div
       [:input {:type "submit" :value "Save"}]]
      [:div
       (coast/link-to "Back" [:{{table}}])])])
