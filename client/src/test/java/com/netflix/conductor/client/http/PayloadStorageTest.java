/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.client.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.netflix.conductor.client.exception.ConductorClientException;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PayloadStorage.class})
public class PayloadStorageTest {

    @InjectMocks PayloadStorage payloadStorage;

    @Mock ClientBase clientBase;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testUploadSuccessfully2xx() throws Exception {

        URI uriMock = mock(URI.class);
        URL urlMock = mock(URL.class);
        HttpURLConnection httpURLConnection = mock(HttpURLConnection.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        whenNew(URI.class).withAnyArguments().thenReturn(uriMock);
        when(uriMock.toURL()).thenReturn(urlMock);
        when(urlMock.openConnection()).thenReturn(httpURLConnection);
        when(httpURLConnection.getResponseCode()).thenReturn(200);
        when(httpURLConnection.getOutputStream()).thenReturn(outputStream);

        String payload = "my payload my payload my payload my payload";
        InputStream payloadInputStream = IOUtils.toInputStream(payload, Charset.defaultCharset());

        payloadStorage.upload("http://url", payloadInputStream, payload.length());

        assertArrayEquals(payload.getBytes(Charset.defaultCharset()), outputStream.toByteArray());
        verify(httpURLConnection).disconnect();
    }

    @Test
    public void testUploadFailure4xx() throws Exception {

        // set expected exception
        expectedException.expect(ConductorClientException.class);
        expectedException.expectMessage("Unable to upload. Response code: 400");

        URI uriMock = mock(URI.class);
        URL urlMock = mock(URL.class);
        HttpURLConnection httpURLConnection = mock(HttpURLConnection.class);
        OutputStream outputStream = new ByteArrayOutputStream();

        whenNew(URI.class).withAnyArguments().thenReturn(uriMock);
        when(uriMock.toURL()).thenReturn(urlMock);
        when(urlMock.openConnection()).thenReturn(httpURLConnection);
        when(httpURLConnection.getResponseCode()).thenReturn(400);
        when(httpURLConnection.getOutputStream()).thenReturn(outputStream);

        String payload = "my payload my payload my payload my payload";
        InputStream payloadInputStream = IOUtils.toInputStream(payload, Charset.defaultCharset());

        payloadStorage.upload("http://url", payloadInputStream, payload.length());

        verify(httpURLConnection).disconnect();
    }

    @Test
    public void testUploadInvalidUrl() {

        // set expected exception
        expectedException.expect(ConductorClientException.class);
        expectedException.expectMessage("Invalid path specified: http://invalidUrl/^");

        payloadStorage.upload("http://invalidUrl/^", null, 0);
    }

    @Test
    public void testUploadIOException() throws Exception {

        // set expected exception
        expectedException.expect(ConductorClientException.class);
        expectedException.expectMessage("Error uploading to path: http://url");

        URI uriMock = mock(URI.class);
        URL urlMock = mock(URL.class);

        whenNew(URI.class).withAnyArguments().thenReturn(uriMock);
        when(uriMock.toURL()).thenReturn(urlMock);
        when(urlMock.openConnection()).thenThrow(new IOException("my exception"));

        payloadStorage.upload("http://url", null, 0);
    }
}
