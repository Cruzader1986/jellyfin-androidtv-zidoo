package org.jellyfin.androidtv.ui.presentation;

import static org.jellyfin.androidtv.util.ImageUtils.ASPECT_RATIO_BANNER;
import static org.jellyfin.androidtv.util.ImageUtils.ASPECT_RATIO_POSTER;
import static org.jellyfin.androidtv.util.ImageUtils.ASPECT_RATIO_POSTER_WIDE;
import static org.jellyfin.androidtv.util.ImageUtils.ASPECT_RATIO_SQUARE;
import static org.jellyfin.androidtv.util.ImageUtils.ASPECT_RATIO_THUMB;
import static org.koin.java.KoinJavaComponent.inject;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.CardInfoType;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.RatingType;
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior;
import org.jellyfin.androidtv.ui.card.LegacyImageCardView;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.ImageUtils;
import org.jellyfin.androidtv.util.TimeUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.sdk.compat.ModelCompat;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.dto.UserItemDataDto;
import org.jellyfin.apiclient.model.entities.LocationType;
import org.jellyfin.sdk.model.api.SearchHint;
import org.koin.java.KoinJavaComponent;

import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Lazy;
import timber.log.Timber;

public class CardPresenter extends Presenter {
    public static final int DEFAULT_CARD_HEIGHT = 300;
    public static final int MAX_CARD_HEIGHT = 700;
    public static final int MIN_CARD_WIDTH = 50;
    private static final ImageType DEFAULT_IMAGE_TYPE = ImageType.POSTER;

    // setup all none ASPECT_RATIO_POSTER default cases
    private static final HashMap<BaseRowItem.ItemType, Pair<ImageType, Double>> DEFAULT_ROW_TYPE_TO_IMAGE_MAP;
    static {
        DEFAULT_ROW_TYPE_TO_IMAGE_MAP = new HashMap<>();
        DEFAULT_ROW_TYPE_TO_IMAGE_MAP.put(BaseRowItem.ItemType.Chapter, new Pair<>(ImageType.THUMB, ASPECT_RATIO_THUMB));
        DEFAULT_ROW_TYPE_TO_IMAGE_MAP.put(BaseRowItem.ItemType.GridButton, new Pair<>(ImageType.POSTER, ASPECT_RATIO_POSTER_WIDE));
        DEFAULT_ROW_TYPE_TO_IMAGE_MAP.put(BaseRowItem.ItemType.SeriesTimer, new Pair<>(ImageType.THUMB, ASPECT_RATIO_THUMB));
    }

    private static final HashMap<BaseItemType, Pair<ImageType, Double>> DEFAULT_BASE_TYPE_TO_IMAGE_MAP;
    static {
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP = new HashMap<>();
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP.put(BaseItemType.Episode, new Pair<>(ImageType.THUMB, ASPECT_RATIO_THUMB));
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP.put(BaseItemType.Audio, new Pair<>(ImageType.POSTER, ASPECT_RATIO_SQUARE));
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP.put(BaseItemType.MusicGenre, new Pair<>(ImageType.POSTER, ASPECT_RATIO_SQUARE));
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP.put(BaseItemType.MusicAlbum, new Pair<>(ImageType.POSTER, ASPECT_RATIO_SQUARE));
        DEFAULT_BASE_TYPE_TO_IMAGE_MAP.put(BaseItemType.MusicArtist, new Pair<>(ImageType.POSTER, ASPECT_RATIO_SQUARE));
    }

    private @NonNull HashMap<BaseRowItem.ItemType, Pair<ImageType, Double>> mRowItemTypeToImageMap = DEFAULT_ROW_TYPE_TO_IMAGE_MAP;
    private @NonNull HashMap<BaseItemType, Pair<ImageType, Double>> mBaseItemTypeToImageMap = DEFAULT_BASE_TYPE_TO_IMAGE_MAP;

    private Integer mStaticHeight;
    public ImageType mImageType = null;
    public Boolean mSquareAspect = null;
    public CardInfoType mInfoType = null;
    private RatingType mRatingType = null;
    public boolean mAllowParentFallback = false;
    // only for Thumb/Banners
    public boolean mAllowBackdropFallback = false;
    // only for Episodes
    public boolean mPreferSeriesForEpisodes = false;
    public boolean mPreferSeasonForEpisodes = false; // if set takes priority over Series

    private final Lazy<ImageHelper> imageHelper = inject(ImageHelper.class);

    /** When set, rows built from this presenter keep its info type (modern home: artwork only). */
    public boolean mForceInfoType = false;

    public CardPresenter() {
        this(CardInfoType.UNDER_FULL, DEFAULT_CARD_HEIGHT);
    }

    public CardPresenter(boolean showInfo) {
        this(showInfo, DEFAULT_CARD_HEIGHT);
    }

    public CardPresenter(boolean showInfo, int staticHeight) {
        this(showInfo ? CardInfoType.UNDER_FULL : CardInfoType.NO_INFO, staticHeight);
    }

    public CardPresenter(CardInfoType infoType, Integer staticHeight) {
        super();
        mInfoType = infoType;
        if (staticHeight != null && staticHeight > 0)
            mStaticHeight = staticHeight;
    }

    public CardPresenter(@NonNull CardPresenter presenter) {
        this();
        mStaticHeight = presenter.mStaticHeight;
        mImageType = presenter.mImageType;
        mSquareAspect = presenter.mSquareAspect;
        mRowItemTypeToImageMap = presenter.mRowItemTypeToImageMap != DEFAULT_ROW_TYPE_TO_IMAGE_MAP ? new HashMap<>(presenter.mRowItemTypeToImageMap) : DEFAULT_ROW_TYPE_TO_IMAGE_MAP;
        mBaseItemTypeToImageMap = presenter.mBaseItemTypeToImageMap != DEFAULT_BASE_TYPE_TO_IMAGE_MAP ? new HashMap<>(presenter.mBaseItemTypeToImageMap) : DEFAULT_BASE_TYPE_TO_IMAGE_MAP;
        mInfoType = presenter.mInfoType;
        mForceInfoType = presenter.mForceInfoType;
        mAllowParentFallback = presenter.mAllowParentFallback;
        mAllowBackdropFallback = presenter.mAllowBackdropFallback;
        mPreferSeriesForEpisodes = presenter.mPreferSeriesForEpisodes;
        mPreferSeasonForEpisodes = presenter.mPreferSeasonForEpisodes;
        mRatingType = presenter.mRatingType;
    }

    class ViewHolder extends Presenter.ViewHolder {
        private static final int BLUR_RESOLUTION = 32;

        private BaseRowItem mItem;
        private final LegacyImageCardView mCardView;
        private Drawable mDefaultCardImage;
        private final LifecycleOwner lifecycleOwner;

        public ViewHolder(View view, LifecycleOwner lifecycleOwner) {
            super(view);

            mCardView = (LegacyImageCardView) view;
            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_video);
            this.lifecycleOwner = lifecycleOwner;
        }

        protected void setItem(@NonNull final BaseRowItem baseRowItem, double aspect) {
            mItem = baseRowItem;
            Context context = mCardView.getContext();
            mDefaultCardImage = mItem.getDefaultImage(context, aspect > 1.0);
            switch (mItem.getItemType()) {
                case BaseItem -> {
                    BaseItemDto itemDto = mItem.getBaseItem();
                    boolean showWatched = false;
                    boolean showProgress = false;
                    if (mItem.getBaseItemType() != null) {
                        switch (mItem.getBaseItemType()) {
                            case Episode, Series, Season, Movie, Video, CollectionFolder, UserView, Folder, Genre -> {
                                showWatched = true;
                                showProgress = true;
                            }
                        }
                    }
                    if (LocationType.Virtual.equals(itemDto.getLocationType())) {
                        mCardView.setBanner((itemDto.getPremiereDate() != null ? TimeUtils.convertToLocalDate(itemDto.getPremiereDate()) : new Date(System.currentTimeMillis() + 1)).getTime() > System.currentTimeMillis() ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                    } else if (LocationType.Offline.equals(itemDto.getLocationType())) {
                        mCardView.setBanner(R.drawable.banner_edge_offline);
                    } else if (Utils.isTrue(itemDto.getIsPlaceHolder())) {
                        mCardView.setBanner(R.drawable.banner_edge_disc);
                    }
                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null && !mItem.isFolder()) {
                        WatchedIndicatorBehavior showIndicator = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getWatchedIndicatorBehavior());
                        if (userData.getPlayed()) {
                            if (showIndicator != WatchedIndicatorBehavior.NEVER && (showIndicator != WatchedIndicatorBehavior.EPISODES_ONLY || itemDto.getBaseItemType() == BaseItemType.Episode))
                                mCardView.setUnwatchedCount(0);
                            else
                                mCardView.setUnwatchedCount(-1);
                        } else if (userData.getUnplayedItemCount() != null) {
                            if (showIndicator == WatchedIndicatorBehavior.ALWAYS)
                                mCardView.setUnwatchedCount(userData.getUnplayedItemCount());
                            else
                                mCardView.setUnwatchedCount(-1);
                        }
                    }
                    int seriesProgress = getSeriesProgress(itemDto, userData);
                    if (showProgress && itemDto.getRunTimeTicks() != null && itemDto.getRunTimeTicks() > 0 && userData != null && userData.getPlaybackPositionTicks() > 0) {
                        mCardView.setProgress(((int) (userData.getPlaybackPositionTicks() * 100.0 / itemDto.getRunTimeTicks()))); // force floating pt math with 100.0
                    } else if (showProgress && seriesProgress > 0) {
                        // Series/season: share of episodes watched
                        mCardView.setProgress(seriesProgress);
                    } else {
                        mCardView.setProgress(0);
                    }
                }
//                case LiveTvChannel ->
//                        mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER); // Channel logos should fit within the view
                case LiveTvProgram -> {
                    BaseItemDto program = mItem.getProgramInfo();
                    if (program.getLocationType() == LocationType.Virtual) {
                        if (program.getStartDate() != null && TimeUtils.convertToLocalDate(program.getStartDate()).getTime() > System.currentTimeMillis()) {
                            mCardView.setBanner(R.drawable.banner_edge_future);
                        }
                    }
                }
            }
            // set all other dynamic properties
            String titleText = switch (mInfoType) {
                case UNDER_FULL -> mItem.getBaseItemType() == BaseItemType.Episode ? mItem.getBaseItem().getSeriesName() : mItem.getName(context);
                default -> mItem.getFullName(context);
            };
            mCardView.setTitleText(titleText);
            mCardView.setContentText(mItem.getSubText(context));
            mCardView.setFocusIcon(mItem);
            mCardView.setInfoOverlayData(mItem);
            mCardView.showFavIcon(mItem.isFavorite());
            mCardView.setPlayingIndicator(mItem.isPlaying());
            if (mInfoType == CardInfoType.NO_INFO && mItem.isFolder()) {
                mCardView.setStatusBadge(ContextCompat.getDrawable(context, R.drawable.ic_folder_stroke_accent), mItem.getChildCountStr(), null);
            }
            mCardView.setRating(mItem, mRatingType != null ? mRatingType : KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getDefaultRatingType()));
        }

        public BaseRowItem getItem() {
            return mItem;
        }

        protected void updateCardViewImage(@Nullable String url, @Nullable String blurHash, double aspect) {
            mCardView.getMainImageView().load(url, blurHash, mDefaultCardImage, aspect, BLUR_RESOLUTION);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder) viewHolder).mCardView.resetCard();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LegacyImageCardView cardView = new LegacyImageCardView(parent.getContext(), mInfoType, parent);
        cardView.setMainImageAspect(getDefaultAspect());

        // preset dimensions, since early code may depend on it!
//        Pair<Integer, Integer> cardHeightWidth = getCardHeightWidth(getDefaultAspect(), mStaticHeight != null ? mStaticHeight : DEFAULT_CARD_HEIGHT, null);
//        cardView.setMainImageHeightWidth(cardHeightWidth.first, cardHeightWidth.second);

        return new ViewHolder(cardView, ViewTreeLifecycleOwner.get(parent));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
//        Timber.d("onBindViewHolder()");
        if (!(item instanceof BaseRowItem)) {
            return;
        }
        BaseRowItem rowItem = (BaseRowItem) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        if (!rowItem.isValid() || holder == null || holder.mCardView == null || holder.mCardView.getContext() == null) {
            return;
        }
        // update aspect for auto types
        Pair<ImageType, Double> imageTypeAndAspect = getImageTypeAndAspect(rowItem);
        holder.mCardView.setMainImageAspect(imageTypeAndAspect.second);
        // set card properties
        holder.setItem(rowItem, imageTypeAndAspect.second);

        Pair<Integer, Integer> cardHeightWidth = getCardHeightWidth(imageTypeAndAspect.second, mStaticHeight != null ? mStaticHeight : DEFAULT_CARD_HEIGHT, null);
//        holder.mCardView.setMainImageHeightWidth(cardHeightWidth.first, cardHeightWidth.second);
        Context context = holder.mCardView.getContext();

        Pair<String, org.jellyfin.apiclient.model.entities.ImageType> imageUrlAndType = getImageUrl(context, rowItem, imageTypeAndAspect.first, null);
        if (imageUrlAndType != null && Utils.isNonEmpty(imageUrlAndType.first)) {
            String blurHash = getImageBlurHashByUrl(rowItem, imageUrlAndType.first, imageUrlAndType.second);
            if (blurHash == null && !rowItem.isPerson())
                Timber.d("Could not get valid blurHash from <%s>", imageUrlAndType.first);

            holder.updateCardViewImage(imageUrlAndType.first, blurHash, imageTypeAndAspect.second);
        } else {
            holder.updateCardViewImage(null, null, imageTypeAndAspect.second);
//            Timber.d("Could not get valid ImageUrl <%s>", rowItem.getFullName(context));
        }
    }

    public Pair<Integer, Integer> getCardHeightWidth() {
        return getCardHeightWidth(null, null, null);
    }

    // FIXME cardHeight vs ImageHeight -> aspect is for image not card ! -> adapt image or card for info area?
    @NonNull
    public Pair<Integer, Integer> getCardHeightWidth(@Nullable Double cardAspect, @Nullable Integer cardHeight, @Nullable Integer cardWidth) {
        int width = MIN_CARD_WIDTH;
        int height = cardHeight != null ? cardHeight : mStaticHeight != null ? mStaticHeight : DEFAULT_CARD_HEIGHT;
        double aspect = cardAspect != null ? cardAspect : getDefaultAspect();
        if (cardWidth != null)
            width = Math.max(cardWidth, MIN_CARD_WIDTH);
        if (cardHeight != null)
            height = Math.min(cardHeight, MAX_CARD_HEIGHT);

        if (cardWidth != null) {
            height = (int) (width / aspect);
            if (height > MAX_CARD_HEIGHT) {
                height = MAX_CARD_HEIGHT;
                width = (int) (height * aspect);   // NOTE: don't round so grids don't overflow!
                Timber.w("cardWidth <%s> is to big for this aspect <%s>, adapting to W<%s>!", cardHeight, aspect, width);
            }
        } else {
            width = (int) (height * aspect);  // NOTE: don't round so grids don't overflow!
            if (width < MIN_CARD_WIDTH) {
                width = MIN_CARD_WIDTH;
                height = (int) (width / aspect);
                Timber.w("CardHeight <%s> is to small for this aspect <%s>, adapting to H<%s>!", cardHeight, aspect, height);
            }
        }

        return new Pair<>(height, width);
    }

    private double getDefaultAspect() {
        if (Utils.isTrue(mSquareAspect)) {
            return ASPECT_RATIO_SQUARE;
        } else {
            ImageType imageType = mImageType != null ? mImageType : DEFAULT_IMAGE_TYPE;
            return switch (imageType) {
                case POSTER -> ASPECT_RATIO_POSTER;
                case THUMB -> ASPECT_RATIO_THUMB;
                case BANNER -> ASPECT_RATIO_BANNER;
            };
        }
    }

    @NonNull
    protected Pair<ImageType, Double> getImageTypeAndAspect(@NonNull final BaseRowItem item) {
        Double aspect = null;
        BaseRowItem.ItemType rowType = item.getItemType();
        BaseItemType baseItemType = item.getBaseItemType();
        if (BaseRowItem.ItemType.SearchHint.equals(rowType)) { // lookup baseType for searches
            try {
                baseItemType = BaseItemType.valueOf(item.getSearchHint().getType());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (mImageType == null) { // get aspect by type
            // baseType take priority
            if (baseItemType != null && mBaseItemTypeToImageMap.containsKey(baseItemType)) {
                return mBaseItemTypeToImageMap.get(baseItemType);
            }
            if (rowType != null && mRowItemTypeToImageMap.containsKey(rowType)) {
                return mRowItemTypeToImageMap.get(rowType);
            }
            // handle Primary defaults
            if (rowType != null) {
                switch (rowType) {
                    case BaseItem:
                        aspect = item.getBaseItem() != null ? item.getBaseItem().getPrimaryImageAspectRatio() : null;
                        break;
                    case LiveTvChannel:
                        aspect = item.getChannelInfo() != null ? item.getChannelInfo().getPrimaryImageAspectRatio() : null;
                        break;
                    case LiveTvRecording:
                        // FIXME why we use ASPECT_RATIO_POSTER_WIDE here?
                        aspect = item.getRecordingInfo() != null ? Utils.getSafeValue(item.getRecordingInfo().getPrimaryImageAspectRatio(), ASPECT_RATIO_POSTER_WIDE) : ASPECT_RATIO_POSTER_WIDE;
                        break;
                    case LiveTvProgram:
                        // FIXME is this still the case for v10.8 ?
                        // The server reports the incorrect image aspect ratio for movies, so we are overriding it here
                        if (item.getProgramInfo() != null && !Utils.isTrue(item.getProgramInfo().getIsMovie())) {
                            aspect = item.getProgramInfo().getPrimaryImageAspectRatio();
                        }
                        break;
                }
                // handle slightly off values, round to next common aspect
                if (aspect != null) {
                    if (aspect < 0.1 || aspect > 10.0)
                        aspect = null;
                    else if (Math.abs(aspect - ASPECT_RATIO_POSTER) < 0.1)
                        aspect = ASPECT_RATIO_POSTER;
                    else if (Math.abs(aspect - ASPECT_RATIO_POSTER_WIDE) < 0.1)
                        aspect = ASPECT_RATIO_POSTER_WIDE;
                    else if (Math.abs(aspect - ASPECT_RATIO_SQUARE) < 0.2)
                        aspect = ASPECT_RATIO_SQUARE;
                    else if (Math.abs(aspect - ASPECT_RATIO_THUMB) < 0.2)
                        aspect = ASPECT_RATIO_THUMB;
                    else if (Math.abs(aspect - ASPECT_RATIO_BANNER) < 0.2)
                        aspect = ASPECT_RATIO_BANNER;
                }
            }
        }
        ImageType imageType = mImageType != null ? mImageType : DEFAULT_IMAGE_TYPE;
        if (Utils.isTrue(mSquareAspect)) {
            aspect = ASPECT_RATIO_SQUARE;
        }
        if (aspect != null) {
            // estimate by aspect
            imageType = aspect <= 1.0 ? ImageType.POSTER : aspect > 5.0 ? ImageType.BANNER : ImageType.THUMB;
        } else if (BaseItemType.Audio.equals(baseItemType) ||
                BaseItemType.MusicArtist.equals(baseItemType) ||
                BaseItemType.MusicAlbum.equals(baseItemType) ||
                BaseItemType.MusicGenre.equals(baseItemType)) {
            // Always square for music types?
            aspect = ASPECT_RATIO_SQUARE;
        } else {
            aspect = switch (imageType) {
                case POSTER -> ASPECT_RATIO_POSTER;
                case THUMB -> ASPECT_RATIO_THUMB;
                case BANNER -> ASPECT_RATIO_BANNER;
            };
        }
        return new Pair<>(imageType, aspect);
    }

    private final Pattern RegExTagPattern = Pattern.compile("tag=([0-9a-fA-F]{32})");

    @Nullable
    private String getTagIdFromUrl(@NonNull String imageUrl) {
        try {
            Matcher matcher = RegExTagPattern.matcher(imageUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private String getImageBlurHashByUrl(@NonNull final BaseRowItem rowItem, @NonNull String imageUrl, @NonNull org.jellyfin.apiclient.model.entities.ImageType imageType) {
        if (Utils.isNonEmpty(imageUrl)) {
            String tagId = getTagIdFromUrl(imageUrl);
            if (Utils.isNonEmpty(tagId)) {
                if (rowItem.isBaseItem() && rowItem.getBaseItem() != null && rowItem.getBaseItem().getImageBlurHashes() != null) {
                    if (rowItem.getBaseItem().getImageBlurHashes().containsKey(imageType)) {
                        return rowItem.getBaseItem().getImageBlurHashes().get(imageType).get(tagId);
                    }
                } else if (rowItem.isPerson() && rowItem.getPerson() != null && rowItem.getPerson().getImageBlurHashes() != null) {
                    if (rowItem.getPerson().getImageBlurHashes().containsKey(ModelCompat.asSdk(imageType))) {
                        return rowItem.getPerson().getImageBlurHashes().get(ModelCompat.asSdk(imageType)).get(tagId);
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    protected Pair<String, org.jellyfin.apiclient.model.entities.ImageType> getImageUrl(Context context, @NonNull final BaseRowItem rowItem, org.jellyfin.androidtv.constant.ImageType imageType,@Nullable Integer maxHeight) {
        String imageUrl = null;
        org.jellyfin.apiclient.model.entities.ImageType outImageType = switch (imageType) {
            case POSTER -> org.jellyfin.apiclient.model.entities.ImageType.Primary;
            case THUMB -> org.jellyfin.apiclient.model.entities.ImageType.Thumb;
            case BANNER -> org.jellyfin.apiclient.model.entities.ImageType.Banner;
        };
        BaseItemDto baseItem = rowItem.getBaseItem();
        switch (rowItem.getItemType()) {
            case BaseItem, LiveTvProgram, LiveTvRecording -> {
                // Episodes rarely have a Thumb image; their still is the Primary image (16:9). Use the episode's own
                // images before any series/season fallback, unless series/season images are explicitly preferred.
                if (baseItem.getBaseItemType() == BaseItemType.Episode && imageType == ImageType.THUMB
                        && !mPreferSeriesForEpisodes && !mPreferSeasonForEpisodes) {
                    org.jellyfin.sdk.model.api.BaseItemDto episode = ModelCompat.asSdk(baseItem);
                    imageUrl = imageHelper.getValue().getImageUrl(episode, org.jellyfin.sdk.model.api.ImageType.THUMB, true, maxHeight, null, false, false, false);
                    if (imageUrl == null) {
                        imageUrl = imageHelper.getValue().getImageUrl(episode, org.jellyfin.sdk.model.api.ImageType.PRIMARY, true, maxHeight, null, false, false, false);
                        if (imageUrl != null) outImageType = org.jellyfin.apiclient.model.entities.ImageType.Primary;
                    }
                }
                if (imageUrl == null) imageUrl = imageHelper.getValue().getImageUrl(ModelCompat.asSdk(baseItem), ModelCompat.asSdk(outImageType), true, maxHeight, null,
                        mAllowParentFallback, mPreferSeriesForEpisodes, mPreferSeasonForEpisodes);
                // only for none Poster
                if (imageUrl == null && mAllowBackdropFallback && imageType != ImageType.POSTER) {
                    outImageType = org.jellyfin.apiclient.model.entities.ImageType.Backdrop;
                    imageUrl = imageHelper.getValue().getImageUrl(ModelCompat.asSdk(baseItem), ModelCompat.asSdk(outImageType), true, maxHeight, null,
                            mAllowParentFallback, mPreferSeriesForEpisodes, mPreferSeasonForEpisodes);
                }
            }
            case SearchHint -> {
                SearchHint searchHint = rowItem.getSearchHint();
                if (ImageType.THUMB == imageType && searchHint != null && Utils.isNonEmpty(searchHint.getThumbImageItemId()) && Utils.isNonEmpty(searchHint.getThumbImageTag())) {
                    imageUrl = ImageUtils.getImageUrl(searchHint.getThumbImageItemId(), org.jellyfin.apiclient.model.entities.ImageType.Thumb, searchHint.getThumbImageTag(), maxHeight);
                    outImageType = org.jellyfin.apiclient.model.entities.ImageType.Thumb;
                }
            }
        }
        if (imageUrl == null) {
            imageUrl = rowItem.getPrimaryImageUrl(context, maxHeight);
            outImageType = org.jellyfin.apiclient.model.entities.ImageType.Primary;
        }
        if (Utils.isEmpty(imageUrl)) {
            return null;
        } else {
            return new Pair<>(imageUrl, outImageType);
        }
    }

    public void setDefaultRowTypeToImage(@NonNull BaseRowItem.ItemType rowType, @NonNull ImageType imageType, double aspect) {
        if (mRowItemTypeToImageMap == DEFAULT_ROW_TYPE_TO_IMAGE_MAP) {
            mRowItemTypeToImageMap = new HashMap<>(DEFAULT_ROW_TYPE_TO_IMAGE_MAP);
        }
        mRowItemTypeToImageMap.put(rowType, new Pair<>(imageType, aspect));
    }

    public void setDefaultBaseTypeToImage(@NonNull BaseItemType itemType, @NonNull ImageType imageType, double aspect) {
        if (mBaseItemTypeToImageMap == DEFAULT_BASE_TYPE_TO_IMAGE_MAP) {
            mBaseItemTypeToImageMap = new HashMap<>(DEFAULT_BASE_TYPE_TO_IMAGE_MAP);
        }
        mBaseItemTypeToImageMap.put(itemType, new Pair<>(imageType, aspect));
    }

    public CardPresenter setImageType(ImageType type) {
        mImageType = type;
        return this;
    }

    public CardPresenter setAllowBackdropFallback(boolean allow) {
        mAllowBackdropFallback = allow;
        return this;
    }

    public CardPresenter setAllowParentFallback(boolean allow) {
        mAllowParentFallback = allow;
        return this;
    }

    public CardPresenter setPreferSeriesForEpisodes(boolean prefer) {
        mPreferSeriesForEpisodes = prefer;
        return this;
    }

    public CardPresenter setPreferSeasonForEpisodes(boolean prefer) {
        mPreferSeasonForEpisodes = prefer;
        return this;
    }

    public CardPresenter setRatingDisplay(RatingType ratingType) {
        mRatingType = ratingType;
        return this;
    }

    public CardPresenter setSquareAspect(boolean isSquare) {
        mSquareAspect = isSquare;
        return this;
    }

    /**
     * Watched share (1-99) of a series or season, 0 when unknown, not started or finished.
     */
    private static int getSeriesProgress(BaseItemDto item, UserItemDataDto userData) {
        if (item == null || userData == null || userData.getPlayed()) return 0;
        BaseItemType type = item.getBaseItemType();
        if (type != BaseItemType.Series && type != BaseItemType.Season) return 0;

        double percent = 0;
        if (userData.getPlayedPercentage() != null) {
            percent = userData.getPlayedPercentage();
        } else if (item.getRecursiveItemCount() != null && item.getRecursiveItemCount() > 0 && userData.getUnplayedItemCount() != null) {
            int total = item.getRecursiveItemCount();
            percent = (total - Math.min(total, userData.getUnplayedItemCount())) * 100.0 / total;
        }
        int rounded = (int) Math.round(percent);
        return rounded > 0 && rounded < 100 ? Math.max(rounded, 2) : 0;
    }
}
