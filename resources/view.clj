(ns {{project}}.views.{{table}}
  (:require [{{project}}.components :as c]
            [coast.core :as coast]))

(defn {{singular}} [m]
  (let [{:keys [{% for col in columns %}{{col}}{% endfor %}]} m]
    [:tr{% for col in columns %}
     [:td {{col}}{% endfor %}]
     [:td
      [:a {:href (coast/url-for ["/{{table}}/:id/edit" m])}
       "Edit {{singular}}"]]
     [:td
      [:a {:href (coast/url-for ["/{{table}}/:id" m])}
       "Show {{singular}}"]]]))

(defn index [request]
  (let [{:keys [{{table}}]} request]
    [:table
     (map {{singular}} {{table}})]
    [:div
     [:a {:href (coast/url-for ["/{{table}}/new"])}
       "New {{singular}}"]]))

(defn show [request]
 (let [{:keys [{{singular}}]} request
       {:keys [{{column_string}}]} {{singular}}]{% for col in columns %}
   [:div {{col}}{% endfor %}]
   [:div
    [:a {:href (coast/url-for ["/{{table}}"])}
     "Back"]]))

(defn fresh [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (c/form {:method :post :action (coast/url-for [:post "/{{table}}"])}{% for col in form_columns %}
        [:div
         [:input {:type "text" :name "{{col}}" :value {{col}}{% endfor %}}]]
        [:div
          [:input {:type "submit" :value "Save"}]]
        [:div
         [:a {:href (coast/url-for ["/{{table}}"])}
          "Back"]])]))

(defn edit [request]
  (let [{:keys [{{singular}} error]} request
        {:keys [{{form_column_string}}]} {{singular}}]
    [:div
      error
      (c/form {:method :post :action (coast/url-for [:put "/{{table}}" {{singular}}{% for col in form_columns %}])}
        [:div
         [:input {:type "text" :name "{{col}}" :value {{col}}{% endfor %}}]]
        [:div
         [:input {:type "submit" :value "Save"}]]
        [:div
         [:a {:href (coast/url-for ["/{{table}}"])}
          "Back"]])]))
