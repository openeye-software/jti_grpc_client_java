/*
# ####################################################################
#
#
#   888888 88888888888 8888888
#       "88b     888       888
#        888     888       888
#        888     888       888
#        888     888       888
#        888     888       888
#        88P     888       888
#        888     888     8888888
#      .d88P
#    .d88P"
#   888P"
#    .d8888b.  8888888b.  8888888b.   .d8888b.
#   d88P  Y88b 888   Y88b 888   Y88b d88P  Y88b
#   888    888 888    888 888    888 888    888
#   888        888   d88P 888   d88P 888
#   888  88888 8888888P"  8888888P"  888
#   888    888 888 T88b   888        888    888
#   Y88b  d88P 888  T88b  888        Y88b  d88P
#    "Y8888P88 888   T88b 888         "Y8888P"
#
#
#
#    .d8888b.  888      8888888 8888888888 888b    888 88888888888
#   d88P  Y88b 888        888   888        8888b   888     888
#   888    888 888        888   888        88888b  888     888
#   888        888        888   8888888    888Y88b 888     888
#   888        888        888   888        888 Y88b888     888
#   888    888 888        888   888        888  Y88888     888
#   Y88b  d88P 888        888   888        888   Y8888     888
#    "Y8888P"  88888888 8888888 8888888888 888    Y888     888
#
#
#       >           APP:  gRPC_TelemetryClient_Java
#       >        AUTHOR:  Jag Channa
#       >       COMPANY:  OpenEye Software
#       >       VERSION:  0.1
#       > REVISION DATE:  2019-04-02
#
# ####################################################################
 */

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import telemetry.Agent;
import telemetry.Agent.GetOperationalStateRequest;
import telemetry.Agent.GetOperationalStateReply;
import telemetry.Agent.SubscriptionRequest;
import telemetry.OpenConfigTelemetryGrpc;
import telemetry.OpenConfigTelemetryGrpc.OpenConfigTelemetryBlockingStub;


public class TelemetryClient {

    // ----------[ CONSTANTS ]----------
    private static final Logger logger = Logger.getLogger(TelemetryClient.class.getName());
    public static final String DEVICE_IP = "10.49.239.48";
    public static final String DEVICE_PASSWORD = "";
    public static final int DEVICE_PORT = 50051;
    public static final String DEVICE_USERNAME = "";
    public static final int SENSOR_FREQUENCY = 5000;
    public static final String SENSOR_PATH = "/interfaces/interface[name='ge-0/0/0']/state/";



    // ----------[ METHOD main() ]----------
    public static void main(String[] args) {

        ManagedChannel channel = null;
        ConnectivityState connectivityState = null;

        try {
            // IMPORTANT NOTE:
            //
            // Ordinarily, we would use a ManagedChannelBuilder to create the gRPC channel, and then use that to
            // construct ManagedChannel for accessing Network Agent gRPC server using the existing channel.
            // However, there appears to be an issue with the Junos Network Agent gRPC server, whereby it is failing
            // to parse a tracing related header in the request.  We see an error that looks like this:
            //
            //      io.grpc.StatusRuntimeException: UNAVAILABLE: {"created":"@1511903558.423607783","description":"EOF",
            //      "file":"../../../../../../../../src/dist/grpc/src/core/lib/iomgr/tcp_posix.c","file_line":235,"grpc_status":14}
            //
            // As per the following issue: https://github.com/grpc/grpc-java/issues/3800, there is a way to disable
            // tracing using NettyChannelBuilder.

            // Create a new builder with the given host (IP address) and port.
            NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(DEVICE_IP, DEVICE_PORT);

            // As per the Important Note above, here is the procedure for disabling tracing.  We have to go this
            // roundabout way because the "setTracingEnabled()" method is protected.
            Field declaredField = NettyChannelBuilder.class.getSuperclass().getDeclaredField("tracingEnabled");
            declaredField.setAccessible(true);
            declaredField.set(nettyChannelBuilder, false);

            // With tracing disabled, we can go ahead and construct the gRPC channel.
            // Since we are not using TLS, be sure to use the "usePlaintext()" method.
            channel = nettyChannelBuilder.usePlaintext().build();

            // Get the gRPC channel state: can be one of {IDLE, CONNECTING, READY, SHUTDOWN, TRANSIENT_FAILURE}
            connectivityState = channel.getState(true);
            logger.info("Value of 'connectivityState': " + connectivityState);

            logger.info("Value of 'channel.isShutdown()': " + channel.isShutdown());
            logger.info("Value of 'channel.isTerminated()': " + channel.isTerminated());
            // Don't proceed further if the channel is shutdown or terminated.
            if(channel.isShutdown() || channel.isTerminated()) {
                logger.warning("Halting Execution as gRPC channel is shutdown or terminated");
            }
            else {
                // We need to use the channel created above to create an OpenConfigTelemetryStub, for which there are
                // two options: (1) An asynchronous stub, or (2) A blocking stub.
                // For simple queries like a "getTelemetryOperationalState()", we employ a blocking stub.
                // For streaming telemetry, we will also employ a blocking stub ... we'll implement asynchronous stub at a later date.

                // To test out our basic connectivity with the gRPC server, let's do a "getTelemetryOperationalState()" query.
                OpenConfigTelemetryBlockingStub getOperationalStateStub = OpenConfigTelemetryGrpc.newBlockingStub(channel);

                // The "getTelemetryOperationalState()" method needs a "GetOperationalStateRequest" as an argument.
                // Let's instantiate it and set a couple of fields.
                // According to the agent.proto file, use 0xFFFFFFF for all subscription identifiers including agent-level operational stats.
                // Set the output verbosity level to BRIEF.
                GetOperationalStateRequest getOperationalStateRequest = GetOperationalStateRequest.newBuilder()
                        .setSubscriptionId(0xFFFFFFFF)
                        .setVerbosity(Agent.VerbosityLevel.BRIEF).build();

                // Issue the "getTelemetryOperationalState()" method and capture the result in a "GetOperationStateReply" object.
                // For now, let's just log the contents to the console.
                GetOperationalStateReply getOperationalStateReply = getOperationalStateStub.getTelemetryOperationalState(getOperationalStateRequest);
                logger.info(getOperationalStateReply.toString());


                // Now, lets subscribe to one or more sensors and capture the data ... for this we need an async stub ...
                OpenConfigTelemetryBlockingStub telemetrySubscribeStub = OpenConfigTelemetryGrpc.newBlockingStub(channel);

                // The "telemetrySubscribe()" method needs a "SubscriptionRequest" object as an argument.
                // The "SubscriptionRequest" object needs a Path list as an argument.
                // Let's instantiate all the Objects and set the appropriate fields.
                SubscriptionRequest subscriptionRequest = SubscriptionRequest.newBuilder()
                        .addPathList(
                                Agent.Path.newBuilder()
                                        .setPath(SENSOR_PATH)
                                        .setSampleFrequency(SENSOR_FREQUENCY)
                                        .build()
                        )
                        .build();

                // Subscribe to the the telemetry stream as per the SubscrtiptionRequest.
                // For now, we are just logging the results to console.
                while(telemetrySubscribeStub.telemetrySubscribe(subscriptionRequest).hasNext()) {
                    logger.info(telemetrySubscribeStub.telemetrySubscribe(subscriptionRequest).next().toString());
                }

                // Shutdown the gRPC channel.
                channel.shutdown();

                // Verify that the gRPC channel is indeed shutdown.
                connectivityState = channel.getState(true);
                logger.info("Value of 'connectivityState': " + connectivityState);
            }
        }
        catch(Exception ex) {
            logger.info(ex.toString());

            // In case any exception occurs, let's make sure to shutdown the gRPC channel so we don't leave anything hanging.
            channel.shutdown();
            connectivityState = channel.getState(true);
            logger.info("Value of 'connectivityState': " + connectivityState);
        }

    }
}
