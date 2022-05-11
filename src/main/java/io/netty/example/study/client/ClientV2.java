package io.netty.example.study.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.study.client.codec.*;
import io.netty.example.study.client.handler.ClientIdleCheckHandler;
import io.netty.example.study.client.handler.KeepaliveHandler;
import io.netty.example.study.client.handler.dispatcher.OperationResultFuture;
import io.netty.example.study.client.handler.dispatcher.RequestPendingCenter;
import io.netty.example.study.client.handler.dispatcher.ResponseDispatcherHandler;
import io.netty.example.study.common.OperationResult;
import io.netty.example.study.common.RequestMessage;
import io.netty.example.study.common.order.OrderOperation;
import io.netty.example.study.util.IdUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.util.concurrent.ExecutionException;

/**
 * v2版本的关键在于能从代码中拿到结果，而不是只能在控制台看。
 * 当然代码上还需进行优化。
 */
public class ClientV2 {
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);

        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap.group(group);

        RequestPendingCenter requestPendingCenter = new RequestPendingCenter();
        KeepaliveHandler keepaliveHandler = new KeepaliveHandler();

        bootstrap.handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new ClientIdleCheckHandler());

                pipeline.addLast(new OrderFrameDecoder());
                pipeline.addLast(new OrderFrameEncoder());

                pipeline.addLast(new OrderProtocolEncoder());
                pipeline.addLast(new OrderProtocolDecoder());

                pipeline.addLast(new ResponseDispatcherHandler(requestPendingCenter));

                pipeline.addLast(new OperationToRequestMessageEncoder());

                pipeline.addLast(new LoggingHandler(LogLevel.INFO));

                pipeline.addLast(keepaliveHandler);
            }
        });

        ChannelFuture channelFuture = bootstrap.connect("127.0.0.1", 8090);

        try {
            channelFuture.sync();
            long streamId = IdUtil.nextId();
            RequestMessage requestMessage = new RequestMessage(streamId, new OrderOperation(1001, "potato"));
            OperationResultFuture operationResultFuture = new OperationResultFuture();
            requestPendingCenter.add(streamId, operationResultFuture);

            channelFuture.channel().writeAndFlush(requestMessage);

            OperationResult operationResult = operationResultFuture.get();

            System.out.println(operationResult);

            channelFuture.channel().closeFuture().sync();

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }

    }
}
