/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.debiandpkgsig.ar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * OutputStream delegating the output to possibly multiple other OutputStreams.
 *
 * @author Markus Kilås
 * @version $Id: MultiOutputStream.java 10991 2019-06-10 11:27:18Z malu9369 $
 */
public class MultiOutputStream extends OutputStream {

    private final OutputStream[] outputs;

    public MultiOutputStream(OutputStream... outputs) {
        this.outputs = outputs;
    }

    public MultiOutputStream(Collection<OutputStream> outputs) {
        this.outputs = outputs.toArray(new OutputStream[0]);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        for (OutputStream output : outputs) {
            output.write(buf);
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        for (OutputStream output : outputs) {
            output.write(buf, off, len);
        }
    }

    @Override
    public void write(int b) throws IOException {
        for (OutputStream output : outputs) {
            output.write(b);
        }
    }

    @Override
    public void flush() throws IOException {
        for (OutputStream output : outputs) {
            output.flush();
        }
    }

    @Override
    public void close() throws IOException {
        for (OutputStream output : outputs) {
            output.close();
        }
    }

}
