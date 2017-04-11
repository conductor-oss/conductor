// Copyright 2017 Netflix, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package httpclient

import (
    "log"
    "net/http"
    "io/ioutil"
    "bytes"
    "strings"
)

type HttpClient struct {
    BaseUrl string
    Headers map[string]string
    PrintLogs bool
}

func NewHttpClient(baseUrl string, headers map[string]string, printLogs bool) *HttpClient {
    httpClient := new(HttpClient)
    httpClient.BaseUrl = baseUrl
    httpClient.Headers = headers
    httpClient.PrintLogs = printLogs
    return httpClient
}

func (c *HttpClient) logSendRequest(url string, requestType string, body string) {
    log.Println("Sending [", requestType, "] request to Server (", url, "):")
    log.Println("Body:")
    log.Println(body)
}

func (c *HttpClient) logResponse(statusCode string, response string) {
    log.Println("Received response from Server (", c.BaseUrl, "):")
    log.Println("Status: ", statusCode)
    log.Println("Response:")
    log.Println(response)
}

func genParamString(paramMap map[string]string) string {

    if paramMap == nil || len(paramMap) == 0 {
        return ""
    }

    output := "?"
    for key, value := range paramMap {
        output += key
        output += "="
        output += value
        output += "&"
    }
    return output
}

func (c *HttpClient) httpRequest(url string, requestType string, headers map[string]string, body string) (string, error) {
    var req *http.Request
    var err error

    if requestType == "GET" {
        req, err = http.NewRequest(requestType, url, nil)
    } else {
        var bodyStr = []byte(body)
        req, err = http.NewRequest(requestType, url, bytes.NewBuffer(bodyStr))
    }

    if err != nil {
        return "", err
    }

    // Default Headers
    for key, value := range c.Headers {
        req.Header.Set(key, value)
    }

    // Custom Headers
    for key, value := range headers {
        req.Header.Set(key, value)
    }

    if c.PrintLogs {
        c.logSendRequest(url, requestType, body)
    }

    client := &http.Client{}
    resp, err := client.Do(req)
    if err != nil {
        return "", err
    }

    defer resp.Body.Close()
    response, err := ioutil.ReadAll(resp.Body)
    responseString := string(response)
    if err != nil {
        log.Println("ERROR reading response for URL: ", url)
        return "", err
    }

    if c.PrintLogs {
        c.logResponse(resp.Status, responseString)
    }
    return responseString, nil
}

func (c *HttpClient) Get(url string, queryParamsMap map[string]string, headers map[string]string) (string, error) {
    urlString := url + genParamString(queryParamsMap)
    resp, err := c.httpRequest(urlString, "GET", headers, "")
    if err != nil {
        log.Println("Http GET Error for URL: ", urlString)
        return "", err
    }
    return resp, nil
}

func (c *HttpClient) Put(url string, queryParamsMap map[string]string, headers map[string]string, body string) (string, error) {
    urlString := url + genParamString(queryParamsMap)
    resp, err := c.httpRequest(urlString, "PUT", headers, body)
    if err != nil {
        log.Println("Http PUT Error for URL: ", urlString, )
        return "", err
    }
    return resp, nil
}

func (c *HttpClient) Post(url string, queryParamsMap map[string]string, headers map[string]string, body string) (string, error) {
    urlString := url + genParamString(queryParamsMap)
    resp, err := c.httpRequest(urlString, "POST", headers, body)
    if err != nil {
        log.Println("Http POST Error for URL: ", urlString)
        return "", err
    }
    return resp, nil
}

func (c *HttpClient) Delete(url string, queryParamsMap map[string]string, headers map[string]string, body string) (string, error) {
    urlString := url + genParamString(queryParamsMap)
    resp, err := c.httpRequest(urlString, "DELETE", headers, body)
    if err != nil {
        log.Println("Http DELETE Error for URL: ", urlString)
        return "", err
    }
    return resp, nil
}

func (c *HttpClient) MakeUrl(path string, args ...string) string {
    url := c.BaseUrl
    r := strings.NewReplacer(args...)
    return url + r.Replace(path)
}
