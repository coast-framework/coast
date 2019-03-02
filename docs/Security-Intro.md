# Security Introduction

* [Session Security](#user-content-session-security)
* [Form Method Spoofing](#user-content-form-method-spoofing)
* [File Uploads](#user-content-file-uploads)

Coast provides a handful of tools to keep your websites secure from common web attacks.

In this guide, we learn about the best practices to keep your Coast applications secure.

## Session Security
Sessions can leak important information if not handled with care.

Coast encrypts and signs all cookies using the `:session-key` defined in the `env.edn` file.

Keep your **production** `:session-key` secret – don't share it with anyone, and never push it to version control systems like Github.

NOTE: **Do not re-use the `env.edn` file's `:session-key` in production. Come up with a new 16-byte key**

### Session Config
Session configuration can be specified as a map passed to `app` in the `src/server.clj` file.

When updating your session configuration, considering the following suggestions:

```clojure
(coast/app {:session {:cookie-attrs {:http-only true, :same-site :strict}}})
```

These are Coast's default settings in development and production

* The `:http-only` value should be set to `true`, as setting it to `false` will make your cookies accessible via JavaScript using `document.cookie`.
* The `:same-site` value should be set to `:strict`, ensuring your session cookie is not visible/accessible via different domains.

## Form Method Spoofing
As HTML forms are only capable of making `GET` and `POST` requests, you cannot use HTTP verbs like `PUT` or `DELETE` to perform resourceful operations via a form's `method` attribute.

To work around this, Coast implements [method spoofing](Request.md#method-spoofing), enabling you to send your intended HTTP method via the request's hidden `_method` input (in a form):

```clojure
[:put "/customers/:customer-id" :customer/update]
```

```clojure
[:form {:method "post" :action "/customers/123"}
  [:input {:type "hidden" :name "_method" :value "put"}]]

; or with form-for
(form-for :customer/update {:customer-id 123})
```

In the example above, adding a hidden input with the name `_method` and value `put` to the form body converts the request HTTP method from `POST` to `PUT`.

Here are a couple of things you should know about method spoofing:

* Coast only spoofs methods where the source HTTP method is `POST`, meaning `GET` requests passing an intended HTTP `_method` are not spoofed.

## File Uploads
Attackers often try to upload malicious files to servers to later execute and gain access to servers to perform some kind of destructive activity.

Besides uploading malicious files, attackers may also try to upload *huge* files so you server stays busy uploading and starts throwing *TIMEOUT* errors for subsequent requests.

To combat this scenario, Coast lets you define the *maximum upload size* processable by your server. This means any file larger than the specified `:max-body` is denied, keeping your server in a healthy state.

Set your `:max-body` value as bytes inside the `app` map in `server.clj`:

```clojure
; src/server.clj

(def app (coast/app {...}))

(coast/server app {:max-body 8388608}) ; the default of 8 MB
```

Here are a few tips to consider when handling file uploads:

* Rename files before uploading/storing. Typically to a `uuid` + `file extension`.
* Don't store uploaded files inside the `public` directory, since `public` files can be accessed directly.
* Don't share the actual location of uploaded files with your users. Instead, consider saving a reference to uploaded file paths in your database (each file having a *unique id*), and set up coast's built in `:storage` middleware to serve those uploaded files via that identifier, like so:

```clojure
(def app (coast/app {:storage (coast/env :file-path)}))
```
