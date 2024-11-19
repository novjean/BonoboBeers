package com.novatech.bonobobeers.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import coil.network.HttpException
import com.novatech.bonobobeers.data.local.BeerDatabase
import com.novatech.bonobobeers.data.local.BeerEntity
import com.novatech.bonobobeers.data.mappers.toBeerEntity
import okio.IOException

@OptIn(ExperimentalPagingApi::class)
class BeerRemoteMediator(
    private val beerDb: BeerDatabase,
    private val beerApi: BeerApi
) : RemoteMediator<Int, BeerEntity>() {

    // when some form of loading wrt pagination
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, BeerEntity>
    ): MediatorResult {
        return try {
            val loadKey = when(loadType){
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return MediatorResult.Success(
                    endOfPaginationReached = true
                )
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                    if(lastItem == null) {
                        1
                    } else {
                        (lastItem.id / state.config.pageSize) +1
                    }
                }
            }

            val beers = beerApi.getBeers(
                page = loadKey,
                pageCount = state.config.pageSize
            )
            beerDb.withTransaction {
                if(loadType == LoadType.REFRESH){
                    //clear cache
                    beerDb.dao.clearAll()
                }
                val beerEntities = beers.map { it.toBeerEntity() }
                beerDb.dao.upsertAll(beerEntities)
            }

            MediatorResult.Success(
                endOfPaginationReached = beers.isEmpty() // no more data to pul in
            )

        } catch(e: IOException){
            MediatorResult.Error(e)
        } catch(e: HttpException){
            MediatorResult.Error(e)
        }
    }
}