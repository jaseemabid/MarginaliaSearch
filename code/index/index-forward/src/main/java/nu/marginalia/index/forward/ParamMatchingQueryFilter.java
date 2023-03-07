package nu.marginalia.index.forward;

import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.filter.QueryFilterStepIf;

public class ParamMatchingQueryFilter implements QueryFilterStepIf {
    private final IndexQueryParams params;
    private final ForwardIndexReader forwardIndexReader;

    public ParamMatchingQueryFilter(IndexQueryParams params, ForwardIndexReader forwardIndexReader) {
        this.params = params;
        this.forwardIndexReader = forwardIndexReader;
    }

    @Override
    public boolean test(long docId) {
        var post = forwardIndexReader.docPost(docId & 0xFFFF_FFFFL);

        if (!validateDomain(post)) {
            return false;
        }

        if (!validateQuality(post)) {
            return false;
        }

        if (!validateYear(post)) {
            return false;
        }

        if (!validateSize(post)) {
            return false;
        }

        if (!validateRank(post)) {
            return false;
        }

        return true;
    }

    private boolean validateDomain(ForwardIndexReader.DocPost post) {
        return params.searchSet().contains(post.domainId());
    }

    private boolean validateQuality(ForwardIndexReader.DocPost post) {
        final var limit = params.qualityLimit();

        if (limit.type() == SpecificationLimitType.NONE) {
            return true;
        }

        final int quality = DocumentMetadata.decodeQuality(post.meta());

        return limit.test(quality);
    }

    private boolean validateYear(ForwardIndexReader.DocPost post) {
        if (params.year().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeYear(post.meta());

        return params.year().test(postVal);
    }

    private boolean validateSize(ForwardIndexReader.DocPost post) {
        if (params.size().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeSize(post.meta());

        return params.size().test(postVal);
    }

    private boolean validateRank(ForwardIndexReader.DocPost post) {
        if (params.rank().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeRank(post.meta());

        return params.rank().test(postVal);
    }

    @Override
    public double cost() {
        return 32;
    }

    @Override
    public String describe() {
        return getClass().getSimpleName();
    }
}