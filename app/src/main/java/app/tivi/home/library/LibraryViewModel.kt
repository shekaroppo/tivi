/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.home.library

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import app.tivi.SharedElementHelper
import app.tivi.data.entities.TiviShow
import app.tivi.datasources.trakt.FollowedShowsDataSource
import app.tivi.datasources.trakt.WatchedShowsDataSource
import app.tivi.extensions.toFlowable
import app.tivi.home.HomeFragmentViewModel
import app.tivi.home.HomeNavigator
import app.tivi.interactors.FetchWatchedShowsInteractor
import app.tivi.interactors.SyncAllFollowedShowsInteractor
import app.tivi.tmdb.TmdbManager
import app.tivi.trakt.TraktAuthState
import app.tivi.trakt.TraktManager
import app.tivi.util.AppRxSchedulers
import app.tivi.util.Logger
import app.tivi.util.NetworkDetector
import app.tivi.util.RxLoadingCounter
import io.reactivex.rxkotlin.Flowables
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class LibraryViewModel @Inject constructor(
    schedulers: AppRxSchedulers,
    watchedShowsDataSource: WatchedShowsDataSource,
    private val watchedShowsInteractor: FetchWatchedShowsInteractor,
    followedDataSource: FollowedShowsDataSource,
    private val followedShowsInteractor: SyncAllFollowedShowsInteractor,
    private val traktManager: TraktManager,
    tmdbManager: TmdbManager,
    private val networkDetector: NetworkDetector,
    logger: Logger
) : HomeFragmentViewModel(traktManager, logger) {
    private val _data = MutableLiveData<LibraryViewState>()
    val data: LiveData<LibraryViewState>
        get() = _data

    private val loadingState = RxLoadingCounter()

    init {
        disposables += Flowables.combineLatest(
                watchedShowsDataSource.data(Unit, 0, 8),
                followedDataSource.data(Unit, 0, 8),
                tmdbManager.imageProvider,
                loadingState.observable.toFlowable(),
                ::LibraryViewState)
                .observeOn(schedulers.main)
                .subscribe(_data::setValue, logger::e)
    }

    fun refresh() {
        disposables += Observables.combineLatest(
                networkDetector.waitForConnection().toObservable(),
                traktManager.state.filter { it == TraktAuthState.LOGGED_IN }
        ).subscribe({ onRefresh() }, logger::e)
    }

    private fun onRefresh() {
        loadingState.addLoader()
        launchInteractor(watchedShowsInteractor).invokeOnCompletion { loadingState.removeLoader() }

        loadingState.addLoader()
        launchInteractor(followedShowsInteractor).invokeOnCompletion { loadingState.removeLoader() }
    }

    fun onWatchedHeaderClicked(navigator: HomeNavigator, sharedElements: SharedElementHelper) {
        navigator.showWatched(sharedElements)
    }

    fun onMyShowsHeaderClicked(navigator: HomeNavigator, sharedElements: SharedElementHelper) {
        navigator.showMyShows(sharedElements)
    }

    fun onItemPostedClicked(navigator: HomeNavigator, show: TiviShow, sharedElements: SharedElementHelper? = null) {
        navigator.showShowDetails(show, sharedElements)
    }
}
