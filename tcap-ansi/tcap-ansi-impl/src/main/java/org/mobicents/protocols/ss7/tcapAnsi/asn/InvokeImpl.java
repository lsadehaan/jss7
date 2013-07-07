/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012.
 * and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.tcapAnsi.asn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;

import org.mobicents.protocols.asn.AsnException;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.tcapAnsi.DialogImpl;
import org.mobicents.protocols.ss7.tcapAnsi.TCAPProviderImpl;
import org.mobicents.protocols.ss7.tcapAnsi.TCAPStackImpl;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.EncodeException;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.ParseException;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.Component;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.PAbortCause;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.Parameter;
import org.mobicents.protocols.ss7.tcapAnsi.api.asn.comp.RejectProblem;
import org.mobicents.protocols.ss7.tcapAnsi.api.tc.component.InvokeClass;
import org.mobicents.protocols.ss7.tcapAnsi.api.tc.component.OperationState;

/**
 * @author baranowb
 * @author amit bhayani
 * @author sergey vetyutnev
 *
 */
public class InvokeImpl implements Invoke {

    // local to stack
    private InvokeClass invokeClass = InvokeClass.Class1;
    private long invokeTimeout = TCAPStackImpl._EMPTY_INVOKE_TIMEOUT;
    private OperationState state = OperationState.Idle;
    private Future timerFuture;
    private OperationTimerTask operationTimerTask = new OperationTimerTask(this);
    private TCAPProviderImpl provider;
    private DialogImpl dialog;

    private Long invokeId;
    private Long correlationId;
    private Invoke correlationInvoke;
    private OperationCode operationCode;
    private Parameter[] parameters;
    private boolean notLast;
    private boolean parameterIsSETStyle;

    public InvokeImpl() {
        // Set Default Class
        this.invokeClass = InvokeClass.Class1;
    }

    public InvokeImpl(InvokeClass invokeClass) {
        if (invokeClass == null) {
            this.invokeClass = InvokeClass.Class1;
        } else {
            this.invokeClass = invokeClass;
        }
    }


    @Override
    public InvokeClass getInvokeClass() {
        return this.invokeClass;
    }

    @Override
    public boolean isNotLast() {
        return notLast;
    }

    @Override
    public void setNotLast(boolean val) {
        notLast = val;
    }

    @Override
    public Long getInvokeId() {
        return this.invokeId;
    }

    @Override
    public void setInvokeId(Long i) {
        if ((i == null) || (i < -128 || i > 127)) {
            throw new IllegalArgumentException("Invoke ID our of range: <-128,127>: " + i);
        }
        this.invokeId = i;
    }

    @Override
    public Long getCorrelationId() {
        return this.correlationId;
    }

    @Override
    public void setCorrelationId(Long i) {
        if ((i == null) || (i < -128 || i > 127)) {
            throw new IllegalArgumentException("Correlation ID our of range: <-128,127>: " + i);
        }
        this.correlationId = i;
    }

    @Override
    public Invoke getCorrelationInvoke() {
        return this.correlationInvoke;
    }

    @Override
    public void setCorrelationInvoke(Invoke val) {
        this.correlationInvoke = val;
    }

    @Override
    public OperationCode getOperationCode() {
        return this.operationCode;
    }

    @Override
    public void setOperationCode(OperationCode i) {
        this.operationCode = i;

    }

    @Override
    public Parameter[] getParameters() {
        return this.parameters;
    }

    @Override
    public void setParameters(Parameter[] p) {
        this.parameters = p;
    }

    @Override
    public boolean getParameterIsSETStyle() {
        return this.parameterIsSETStyle;
    }

    @Override
    public void setParameterIsSETStyle(boolean val) {
        this.parameterIsSETStyle = val;
    }

    @Override
    public ComponentType getType() {
        if (this.isNotLast())
            return ComponentType.Invoke;
        else
            return ComponentType.InvokeLast;
    }


    /*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.asn.Encodable#decode(org.mobicents.protocols .asn.AsnInputStream)
     */
    public void decode(AsnInputStream ais) throws ParseException {

        this.correlationId = null;
        this.correlationInvoke = null;
        this.operationCode = null;
        this.parameters = null;
        this.parameterIsSETStyle = false;

        try {
            if (ais.getTag() == Invoke._TAG_INVOKE_NOT_LAST)
                this.setNotLast(true);
            else
                this.setNotLast(false);

            AsnInputStream localAis = ais.readSequenceStream();

            // invokeId & correlationId
            int tag = localAis.readTag();
            if (tag != Component._TAG_INVOKE_ID || localAis.getTagClass() != Tag.CLASS_PRIVATE || !localAis.isTagPrimitive()) {
                throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalIncorrectComponentPortion,
                        "InvokeID in Invoke has bad tag or tag class or is not primitive: tag=" + tag + ", tagClass=" + localAis.getTagClass());
            }
            byte[] buf = localAis.readOctetString();
            if (buf.length > 2)
                throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalBadlyStructuredCompPortion,
                        "InvokeID in Invoke must be 0, 1 or 2 bytes length, found bytes=" + buf.length);
            if (buf.length >= 1)
                this.setInvokeId((long) buf[0]);
            if (buf.length >= 2)
                this.setCorrelationId((long) buf[1]);

            // operationCode
            tag = localAis.readTag();
            if ((tag != OperationCode._TAG_NATIONAL && tag != OperationCode._TAG_PRIVATE) || localAis.getTagClass() != Tag.CLASS_PRIVATE
                    || !localAis.isTagPrimitive()) {
                throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalIncorrectComponentPortion,
                        "OperationCode in Invoke has bad tag or tag class or is not primitive: tag=" + tag + ", tagClass=" + localAis.getTagClass());
            }
            this.operationCode = TcapFactory.createOperationCode(localAis);

            // Parameters
            tag = localAis.readTag();
            if ((tag != Parameter._TAG_SEQUENCE && tag != Parameter._TAG_SET) || localAis.getTagClass() != Tag.CLASS_PRIVATE
                    || localAis.isTagPrimitive()) {
                throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalIncorrectComponentPortion,
                        "Parameters in Invoke has bad tag or tag class or is primitive: tag=" + tag + ", tagClass=" + localAis.getTagClass());
            }
            if (tag == Parameter._TAG_SEQUENCE)
                parameterIsSETStyle = false;
            else
                parameterIsSETStyle = true;

            AsnInputStream ais2 = localAis.readSequenceStream();
            ArrayList<Parameter> pars = new ArrayList<Parameter>();
            while (true) {
                if (ais2.available() == 0)
                    break;

                ais2.readTag();
                Parameter par = TcapFactory.createParameter(ais2);
                pars.add(par);
            }
            Parameter[] res = new Parameter[pars.size()];
            pars.toArray(res);
            this.setParameters(res);

        } catch (IOException e) {
            throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalBadlyStructuredCompPortion,
                    "IOException while decoding Invoke: " + e.getMessage(), e);
        } catch (AsnException e) {
            throw new ParseException(PAbortCause.BadlyStructuredTransactionPortion, RejectProblem.generalBadlyStructuredCompPortion,
                    "AsnException while decoding Invoke: " + e.getMessage(), e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.asn.Encodable#encode(org.mobicents.protocols .asn.AsnOutputStream)
     */
    public void encode(AsnOutputStream aos) throws EncodeException {
        if (this.getParameterIsSETStyle() && (this.parameters == null || this.parameters.length == 0))
            throw new EncodeException("Error encoding Invoke: for Paramaters SET we have to have at least one parameter");
        if (this.operationCode == null)
            throw new EncodeException("Error encoding Invoke: operationCode is mandatory but is not set");

        try {
            // tag
            if (this.notLast)
                aos.writeTag(Tag.CLASS_PRIVATE, false, Invoke._TAG_INVOKE_NOT_LAST);
            else
                aos.writeTag(Tag.CLASS_PRIVATE, false, Invoke._TAG_INVOKE_LAST);
            int pos = aos.StartContentDefiniteLength();

            // invokeId and correlationId
            byte[] buf;
            if (this.invokeId != null) {
                if (this.correlationId != null) {
                    buf = new byte[2];
                    buf[0] = (byte) (long) this.invokeId;
                    buf[1] = (byte) (long) this.correlationId;
                } else {
                    buf = new byte[1];
                    buf[0] = (byte) (long) this.invokeId;
                }
            } else {
                buf = new byte[0];
            }
            aos.writeOctetString(Tag.CLASS_PRIVATE, Component._TAG_INVOKE_ID, buf);

            // operationCode
            this.operationCode.encode(aos);

            // parameters
            if (this.getParameterIsSETStyle()) {
                aos.writeTag(Tag.CLASS_PRIVATE, false, Parameter._TAG_SET);
            } else {
                aos.writeTag(Tag.CLASS_PRIVATE, false, Parameter._TAG_SEQUENCE);
            }
            int pos2 = aos.StartContentDefiniteLength();
            if (this.parameters != null && this.parameters.length > 0) {
                for (Parameter par : this.parameters) {
                    par.encode(aos);
                }
            }
            aos.FinalizeContent(pos2);

            aos.FinalizeContent(pos);

        } catch (IOException e) {
            throw new EncodeException("IOException while encoding Invoke: " + e.getMessage(), e);
        } catch (AsnException e) {
            throw new EncodeException("AsnException while encoding Invoke: " + e.getMessage(), e);
        }
    }

    /**
     * @return the invokeTimeout
     */
    public long getTimeout() {
        return invokeTimeout;
    }

    /**
     * @param invokeTimeout the invokeTimeout to set
     */
    public void setTimeout(long invokeTimeout) {
        this.invokeTimeout = invokeTimeout;
    }

    // ////////////////////
    // set methods for //
    // relevant data //
    // ///////////////////
    /**
     * @return the provider
     */
    public TCAPProviderImpl getProvider() {
        return provider;
    }

    /**
     * @param provider the provider to set
     */
    public void setProvider(TCAPProviderImpl provider) {
        this.provider = provider;
    }

    /**
     * @return the dialog
     */
    public DialogImpl getDialog() {
        return dialog;
    }

    /**
     * @param dialog the dialog to set
     */
    public void setDialog(DialogImpl dialog) {
        this.dialog = dialog;
    }

    /**
     * @return the state
     */
    public OperationState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public synchronized void setState(OperationState state) {
        if (this.dialog == null) {
            // bad call on server side.
            return;
        }
        OperationState old = this.state;
        this.state = state;
        if (old != state) {

            switch (state) {
                case Sent:
                    // start timer
                    this.startTimer();
                    break;
                case Idle:
                case Reject_W:
                    this.stopTimer();
                    dialog.operationEnded(this);
            }
            if (state == OperationState.Sent) {

            } else if (state == OperationState.Idle || state == OperationState.Reject_W) {

            }

        }
    }

    public void onReturnResultLast() {
        this.setState(OperationState.Idle);

    }

    public void onError() {
        this.setState(OperationState.Idle);

    }

    public void onReject() {
        this.setState(OperationState.Idle);
    }

    public synchronized void startTimer() {
        if (this.dialog == null || this.dialog.getPreviewMode())
            return;

        this.stopTimer();
        if (this.invokeTimeout > 0)
            this.timerFuture = this.provider.createOperationTimer(this.operationTimerTask, this.invokeTimeout);
    }

    public synchronized void stopTimer() {

        if (this.timerFuture != null) {
            this.timerFuture.cancel(false);
            this.timerFuture = null;
        }

    }

    public boolean isErrorReported() {
        if (this.invokeClass == InvokeClass.Class1 || this.invokeClass == InvokeClass.Class2) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isSuccessReported() {
        if (this.invokeClass == InvokeClass.Class1 || this.invokeClass == InvokeClass.Class3) {
            return true;
        } else {
            return false;
        }
    }

    private class OperationTimerTask implements Runnable {
        InvokeImpl invoke;

        OperationTimerTask(InvokeImpl invoke) {
            this.invoke = invoke;
        }

        public void run() {

            // op failed, we must delete it from dialog and notify!
            timerFuture = null;
            setState(OperationState.Idle);
            // TC-L-CANCEL
            ((DialogImpl) invoke.dialog).operationTimedOut(invoke);
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.isNotLast())
            sb.append("InvokeNotLast[");
        else
            sb.append("InvokeLast[");
        if (this.getInvokeId() != null) {
            sb.append("InvokeId=");
            sb.append(this.getInvokeId());
            sb.append(", ");
        }
        if (this.getCorrelationId() != null) {
            sb.append("CorrelationId=");
            sb.append(this.getCorrelationId());
            sb.append(", ");
        }
        if (this.getOperationCode() != null) {
            sb.append("OperationCode=");
            sb.append(this.getOperationCode());
            sb.append(", ");
        }
        if (this.getParameters() != null && this.getParameters().length > 0) {
            sb.append("Parameters=[");
            for (Parameter par : this.getParameters()) {
                sb.append("Parameter=[");
                sb.append(par);
                sb.append("], ");
            }
            sb.append("], ");
        }
        if (this.getInvokeClass() != null) {
            sb.append("InvokeClass=");
            sb.append(this.getInvokeClass());
            sb.append(", ");
        }

        sb.append("State=");
        sb.append(this.state);
        sb.append("]");

        return sb.toString();
    }

}