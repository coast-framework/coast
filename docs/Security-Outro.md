# XSS, Sniffing, XFrame

Common security headers help ensure your web application is secure, whether you run it behind nginx or not.

Coast by default attempts to protect your web app from *XSS* attacks, unwanted *iframe embeds*, and *content-type sniffing*.

### XSS
Coast by default passes this to `app` which results in the header `X-XSS-Protection=1; mode=block` being sent on every response.

```clojure
{:security {:xss-protection {:enable? true, :mode :block}}}
```

### No Sniff
The majority of modern browsers attempts to detect the *Content-Type* of a request by sniffing its content, meaning a file ending in *.txt* could be executed as JavaScript if it contains JavaScript code.

This behavior is disabled by default with the map:

```clojure
{:security {:content-type-options :nosniff}}
```

### XFrame
Coast also makes it easy for you to control the embed behavior of your website inside an iframe.

Available options are `:deny`, `:same-origin` or `:allow-from [http://example.com]`:

The default is `:deny`

```clojure
{:security {:frame-options  :deny}}
```
