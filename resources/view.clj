(ns {{project}}.views.{{table}}
  (:require [{{project}}.components :as c]))

(defn {{singular}} [m]
  [:tr{% for col in columns %}
   [:td (:{{col}} m)]{% endfor %}
   [:td
    [:a {:href (str "/{{table}}/" (:id m) "/edit")} "Edit"]]
   [:td
     [:a {:href (str "/{{table}}/" (:id m))} "Show"]]])

(defn index [{{table}}]
  [:table
   (map {{singular}} {{table}})]
  [:div
   [:a {:href "/{{table}}/new"} "New {{singular}}"]])

(defn show [{{singular}}]
 {% for col in columns %}
 [:div (:{{col}} {{singular}}{% endfor %})]
 [:div
  [:a {:href "/{{table}}"} "Back"]])

(defn new- [{:keys [{{singular}} error]}]
  error
  (c/form {:action "/{{table}}" :method "post"}{% for col in form_columns %}
    [:div
      [:input {:type "text" :name "{{col}}" :value (:{{col}} {{singular}})}]{% endfor %}]
    [:div
      [:input {:type "submit" :value "Create"}]]
    [:div
      [:a {:href "/{{table}}"} "Back"]]))

(defn edit [{:keys [{{singular}} error]}]
  error
  (c/form {:action (str "/{{table}}/" (:id {{singular}})) :method "put"}{% for col in form_columns %}
    [:div
     [:input {:type "text" :name "{{col}}" :value (:{{col}} {{singular}})}]{% endfor %}]
    [:div
     [:input {:type "submit" :value "Update"}]]
    [:div
     [:a {:href "/{{table}}"} "Back"]]))
