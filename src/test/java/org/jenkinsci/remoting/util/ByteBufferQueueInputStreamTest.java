/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.remoting.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class ByteBufferQueueInputStreamTest {
    @Test
    public void readAll() throws Exception {
        String str = "AbCdEfGhIjKlMnOpQrStUvWxYz";

        ByteBufferQueue queue = new ByteBufferQueue(10);
        queue.put(ByteBuffer.wrap(str.getBytes(Charsets.UTF_8)));
        ByteBufferQueueInputStream instance = new ByteBufferQueueInputStream(queue);

        assertThat(read(instance), is(str));
    }

    @Test
    public void readLimit() throws Exception {
        String str = "AbCdEfGhIjKlMnOpQrStUvWxYz";

        ByteBufferQueue queue = new ByteBufferQueue(10);
        queue.put(ByteBuffer.wrap(str.getBytes(Charsets.UTF_8)));
        ByteBufferQueueInputStream instance = new ByteBufferQueueInputStream(queue, 10);

        assertThat(read(instance, 10), is("AbCdEfGhIj"));
    }

    @Test
    public void readSome() throws Exception {
        String str = "AbCdEfGhIjKlMnOpQrStUvWxYz";

        ByteBufferQueue queue = new ByteBufferQueue(10);
        queue.put(ByteBuffer.wrap(str.getBytes(Charsets.UTF_8)));
        ByteBufferQueueInputStream instance = new ByteBufferQueueInputStream(queue);

        assertThat(read(instance, 10), is("AbCdEfGhIj"));
    }

    @Test
    public void skipRead() throws Exception {
        String str = "AbCdEfGhIjKlMnOpQrStUvWxYz";

        ByteBufferQueue queue = new ByteBufferQueue(10);
        queue.put(ByteBuffer.wrap(str.getBytes(Charsets.UTF_8)));
        ByteBufferQueueInputStream instance = new ByteBufferQueueInputStream(queue);

        StringBuffer buf = new StringBuffer();
        int b;
        do {
            if (instance.skip(1) != 1) {
                b = -1;
            } else {
                b = instance.read();
                if (b != -1) {
                    buf.append((char) b);
                }
            }
        } while (b != -1);

        assertThat(buf.toString(), is("bdfhjlnprtvxz"));
    }

    @Test
    public void markRead() throws Exception {
        String str = "AbCdEfGhIjKlMnOpQrStUvWxYz";

        ByteBufferQueue queue = new ByteBufferQueue(10);
        queue.put(ByteBuffer.wrap(str.getBytes(Charsets.UTF_8)));
        ByteBufferQueueInputStream instance = new ByteBufferQueueInputStream(queue);
        assumeThat(instance.markSupported(), is(true));
        instance.mark(4);
        instance.read(new byte[3]);
        instance.reset();
        assertThat(read(instance), is(str));
    }

    private static String read(InputStream is) throws IOException {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        int b;
        while (-1 != (b = is.read())) {
            tmp.write(b);
        }
        return new String(tmp.toByteArray(), Charsets.UTF_8);
    }

    private static String read(InputStream is, int count) throws IOException {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        int b;
        while (count > 0 && -1 != (b = is.read())) {
            tmp.write(b);
            count--;
        }
        return new String(tmp.toByteArray(), Charsets.UTF_8);
    }
}
