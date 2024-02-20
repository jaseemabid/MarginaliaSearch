package nu.marginalia.query;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.index.api.IndexDomainLinksApiGrpc;
import nu.marginalia.index.api.RpcDomainIdCount;
import nu.marginalia.index.api.RpcDomainIdList;
import nu.marginalia.index.api.RpcDomainIdPairs;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QueryGRPCDomainLinksService extends IndexDomainLinksApiGrpc.IndexDomainLinksApiImplBase {
    private static final Logger logger = LoggerFactory.getLogger(QueryGRPCDomainLinksService.class);
    private final GrpcMultiNodeChannelPool<IndexDomainLinksApiGrpc.IndexDomainLinksApiBlockingStub> channelPool;

    @Inject
    public QueryGRPCDomainLinksService(GrpcChannelPoolFactory channelPoolFactory) {
        channelPool = channelPoolFactory.createMulti(ServiceId.Index, IndexDomainLinksApiGrpc::newBlockingStub);
    }

    @Override
    public void getAllLinks(nu.marginalia.index.api.Empty request,
                            StreamObserver<RpcDomainIdPairs> responseObserver) {
        channelPool.callEachSequential(stub -> stub.getAllLinks(request))
                .forEach(
                        iter -> iter.forEachRemaining(responseObserver::onNext)
                );

        responseObserver.onCompleted();
    }

    @Override
    public void getLinksFromDomain(nu.marginalia.index.api.RpcDomainId request,
                                   StreamObserver<RpcDomainIdList> responseObserver) {
        var rspBuilder = RpcDomainIdList.newBuilder();

        channelPool.callEachSequential(stub -> stub.getLinksFromDomain(request))
                .map(RpcDomainIdList::getDomainIdList)
                .forEach(rspBuilder::addAllDomainId);

        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinksToDomain(nu.marginalia.index.api.RpcDomainId request,
                                 StreamObserver<RpcDomainIdList> responseObserver) {
        var rspBuilder = RpcDomainIdList.newBuilder();

        channelPool.callEachSequential(stub -> stub.getLinksToDomain(request))
                .map(RpcDomainIdList::getDomainIdList)
                .forEach(rspBuilder::addAllDomainId);

        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void countLinksFromDomain(nu.marginalia.index.api.RpcDomainId request,
                                     StreamObserver<RpcDomainIdCount> responseObserver) {

        int sum = channelPool.callEachSequential(stub -> stub.countLinksFromDomain(request))
                .mapToInt(RpcDomainIdCount::getIdCount)
                .sum();

        var rspBuilder = RpcDomainIdCount.newBuilder();
        rspBuilder.setIdCount(sum);
        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void countLinksToDomain(nu.marginalia.index.api.RpcDomainId request,
                                   io.grpc.stub.StreamObserver<nu.marginalia.index.api.RpcDomainIdCount> responseObserver) {

        int sum = channelPool.callEachSequential(stub -> stub.countLinksToDomain(request))
                .mapToInt(RpcDomainIdCount::getIdCount)
                .sum();

        var rspBuilder = RpcDomainIdCount.newBuilder();
        rspBuilder.setIdCount(sum);
        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

}
