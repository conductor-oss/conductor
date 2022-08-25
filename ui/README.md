## Conductor UI

The UI is a standard `create-react-app` React Single Page Application (SPA). To get started, with Node 14 and `yarn` installed, first run `yarn install` from within the `/ui` directory to retrieve package dependencies.

For more information regarding CRA configuration and usage, see the official [doc site](https://create-react-app.dev/).

> ### For upgrading users
>
> The UI is designed to operate directly with the Conductor Server API. A Node `express` backend is no longer required.

### Development Server

To run the UI on the bundled development server, run `yarn run start`. Navigate your browser to `http://localhost:5000`.

#### Reverse Proxy configuration

The default setup expects that the Conductor Server API will be available at `localhost:8080/api`. You may select an alternate port and hostname, or rewrite the API path by editing `setupProxy.js`. Note that `setupProxy.js` is used ONLY by the development server.

### Hosting for Production

There is no need to "build" the project unless you require compiled assets to host on a production web server. In this case, the project can be built with the command `yarn build`. The assets will be produced to `/build`.

Your hosting environment should make the Conductor Server API available on the same domain. This avoids complexities regarding cross-origin data fetching. The default path prefix is `/api`. If a different prefix is desired, `plugins/fetch.js` can be modified to customize the API fetch behavior.

See `docker/serverAndUI` for an `nginx` based example.

### Customization Hooks

For ease of maintenance, a number of touch points for customization have been removed to `/plugins`.

- `AppBarModules.jsx`
- `AppLogo.jsx`
- `env.js`
- `fetch.js`

### Authentication

We recommend that authentication & authorization be de-coupled from the UI and handled at the web server/access gateway.

#### Examples (WIP)

- Basic Auth (username/password) with `nginx`
- Commercial IAM Vendor
- Node `express` server with `passport.js`
