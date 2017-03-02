import path from 'path';
import webpack from 'webpack';
import fs from 'fs';
import merge from 'lodash.merge';
import _ from 'lodash';

var nodeModules = {};
fs.readdirSync('node_modules')
    .filter(function (x) {
        return ['.bin'].indexOf(x) === -1;
    })
    .forEach(function (mod) {
        nodeModules[mod] = 'commonjs ' + mod;
    });

console.log('process.argv =====> ', process.argv);
const VERBOSE = false;
const DEBUG = _.includes(process.argv, 'watch');
const WATCH = _.includes(process.argv, 'watch');
console.log('DEBUG =====> ', DEBUG);
console.log('WATCH =====> ', WATCH);

const JS_LOADER = {
    test: /\.(js|jsx)$/,
    exclude: /(node_modules|bower_components)/,
    loader: 'babel',
    query: {
        presets: ['es2015', 'react', 'stage-0']
    }
};

const JS_LOADER_DEBUG = {
    test: /\.(js|jsx)$/,
    exclude: /(node_modules|bower_components)/,
    loader: 'babel',
    query: {
        presets: ['es2015', 'react', 'stage-0', 'react-hmre'],
    }
};

const config = {
    plugins: [
        new webpack.optimize.OccurenceOrderPlugin(),
        new webpack.DefinePlugin({
            'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development')
        })
    ],
    stats: {
        colors: true,
        reasons: false,
        hash: false,
        version: false,
        timings: false,
        chunks: false,
        chunkModules: false,
        cached: false,
        cachedAssets: false
    },
    resolve: {
        extensions: ['', '.webpack.js', '.web.js', '.js', '.jsx', '.json'],
    },
    module: {
        loaders: [
            {
                test: /\.json$/,
                loader: 'json-loader',
            }, {
                test: /\.txt$/,
                loader: 'raw-loader',
            }, {
                test: /\.(png|jpg|jpeg|gif|svg|woff|woff2)$/,
                loader: 'url-loader?limit=10000',
            }, {
                test: /\.(eot|ttf|wav|mp3)$/,
                loader: 'file-loader',
            },
        ],
    },
};

const appConfig = merge({}, config, {
    entry: [
        ...(WATCH ? ['webpack-hot-middleware/client'] : []),
        'babel-polyfill', './src/app.js',
    ],
    output: {
        path: path.join(__dirname, './dist/public/js'),
        filename: 'app.js',
    },
    devtool: WATCH ? 'cheap-module-eval-source-map' : false,
    plugins: [
        ...config.plugins,
        ...(!DEBUG ? [
                new webpack.optimize.DedupePlugin(),
                new webpack.optimize.UglifyJsPlugin({
                    compress: {
                        warnings: VERBOSE,
                    },
                }),
                new webpack.optimize.AggressiveMergingPlugin(),
            ] : []),
        ...(WATCH ? [
                new webpack.HotModuleReplacementPlugin(),
                new webpack.NoErrorsPlugin(),
            ] : []),
    ],
    module: {
        loaders: [
            WATCH ? JS_LOADER_DEBUG : JS_LOADER,
            ...config.module.loaders,
            {
                test: /\.css$/,
                loader: 'style-loader/useable!css-loader!postcss-loader',
            },
        ],
    }
});

const serverConfig = merge({}, config, {
    serverConfig: true,
    entry: ['./src/server.js'],
    devtool: 'source-map',
    target: 'node',
    output: {
        filename: './dist/server.js',
    },
    module: {
        loaders: [
            JS_LOADER,
            ...config.module.loaders
        ]
    },
    externals: nodeModules,
    plugins: [
        ...config.plugins,
        new webpack.BannerPlugin('require("source-map-support").install();',
            {raw: true, entryOnly: false})
    ]
});

export default [appConfig, serverConfig];
