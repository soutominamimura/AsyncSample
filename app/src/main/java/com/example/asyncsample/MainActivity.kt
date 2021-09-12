package com.example.asyncsample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.UserDictionary.Words.APP_ID
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.os.HandlerCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionHandler

class MainActivity : AppCompatActivity() {

    //クラス内のprivate定数を宣言するためにcompqnion objectブロックとする
    companion object{
        //ログに記載するタグ用の文字列
        private const val DEBUG_TAG = "AsyncSample"
        //お天気情報のURL
        private const val WEATHERINFO_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja"
        //お天気APIにアクセスするためのAPIキー
        private const val APP_ID = "a4be7f2d709c18f11e8b7e5c448cefaf"
    }

    //リストビューに表示させるリストデータ
    private var _list: MutableList<MutableMap<String,String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _list = createList()

        val lvCityList = findViewById<ListView>(R.id.lvCityList)
        val from = arrayOf("name")
        val to = intArrayOf(android.R.id.text1)
        val adapter = SimpleAdapter(this@MainActivity,_list,android.R.layout.simple_list_item_1,from,to)
        lvCityList.adapter = adapter
        lvCityList.onItemClickListener = ListItemClickListener()

    }

    //リストビューに表示させる天気ポイントリストデータを生成するメソッド
    private fun createList(): MutableList<MutableMap<String,String>>{
        var list: MutableList<MutableMap<String,String>> = mutableListOf()

        var city = mutableMapOf("name" to "大阪","q" to "Osaka")
        list.add(city)
        city = mutableMapOf("name" to "神戸" , "q" to "Kobe")
        list.add(city)
        city = mutableMapOf("name" to "京都" , "q" to "Kyoto")
        list.add(city)
        city = mutableMapOf("name" to "大津" , "q" to "Otu")
        list.add(city)
        city = mutableMapOf("name" to "奈良" , "q" to "Nara")
        list.add(city)

        return list
    }

    //お天気情報の取得処理を行うメソッド
    @UiThread
    private fun receiveWeatherInfo(urlFull:String){
        //ここに非同期で天気情報を取得する処理を記述する
        val handler = HandlerCompat.createAsync(mainLooper)
        val backgroundReceiver = WeatherInfoBackgroundReceiver(handler,urlFull)
        val executeService = Executors.newSingleThreadExecutor()
        executeService.submit(backgroundReceiver)

    }
    //非同期でお天気情報APIにアクセスするためのクラス
    private inner class WeatherInfoBackgroundReceiver(handler: Handler,url: String): Runnable {
        //ハンドラオブジェクト
        private val _handler = handler
        //お天気情報を取得するURL
        private val _url = url

        @WorkerThread
        override fun run() {
            //WebAPIにアクセスするコードを記述
            //天気情報サービスから取得したJson文字列。天気予報が格納されている
            var result = ""
            //URLオブジェクトを生成
            val url = URL(_url)
            //URLオブジェクトからHttpURLConnectionオブジェクトを取得
            val con = url.openConnection() as? HttpURLConnection
            //conがnullじゃないならば
            con?.let {
                try {
                    //接続に使っても良い時間を設定
                    it.connectTimeout = 1000
                    //データ取得に使っても良い時間
                    it.readTimeout = 1000
                    //HTTPメソッドをGETに設定
                    it.requestMethod = "GET"
                    //接続
                    it.connect()
                    //HttpURLConnectionオブジェクトからレスポンスデータを取得
                    val stream = it.inputStream
                    //レスポンスデータであるInputStreamを文字列に変換
                    result = is2String(stream)
                    //InputStreamオブジェクトを開放
                    stream.close()
                } catch (ex: SocketTimeoutException) {
                    Log.w(DEBUG_TAG, "通信タイムアウト", ex)
                }
                //HttpURLConnectionオブジェクトを開放
                it.disconnect()
            }
            val postExecutor = WeatherInfoPostExeutor(result)
            _handler.post(postExecutor)
        }
    }
        private fun is2String(stream:InputStream):String{
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(stream,"UTF-8"))
            var line = reader.readLine()
            while(line != null){
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            return sb.toString()

    }

    //非同期でお天気情報を取得した後にUIスレッドでその情報を表示するためのクラス
    private inner class WeatherInfoPostExeutor(result:String): Runnable {
        //取得したお天気情報JSON文字列
        private val _result = result

        @UiThread
        override fun run(){
            //ここにWebAPIにアクセスするコードを記述
            //ルートJSONオブジェクトを生成
            val rootJSON = JSONObject(_result)
            //都市名文字列を取得
            val ctiyName = rootJSON.getString("name")
            //緯度経度文字列を取得
            val coordJSON = rootJSON.getJSONObject("coord")
            //緯度情報文字列を取得
            val latitude = coordJSON.getString("lat")
            //経度情報文字列を取得
            val longitude = coordJSON.getString("lon")
            //天気情報JSONオブジェクトを取得
            val weatherJSONArray = rootJSON.getJSONArray("weather")
            //現在の天気情報JSONオブジェクトを取得
            val weatherJSON = weatherJSONArray.getJSONObject(0)
            //現在の天気情報文字列を取得
            val weather = weatherJSON.getString("description")
            //画面に表示する「○○のお天気」文字列を生成
            val telop = "${ctiyName}の天気"
            //天気の詳細情報を表示する文字列を生成
            val desc = "現在は${weather}です。経度は${latitude}度で経度は${longitude}度です。"
            //天気情報を表示するTextViewを取得
            val tvweatherTelop = findViewById<TextView>(R.id.tvWeatherTelop)
            val tvWeatherDesc = findViewById<TextView>(R.id.tvWeatherDesc)
            //天気情報の表示
            tvweatherTelop.text= telop
            tvWeatherDesc.text = desc

        }
    }

    //リストがタップされた時の処理が記述されたリスナクラス
    private inner class ListItemClickListener: AdapterView.OnItemClickListener{
        override  fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id:Long){
            val item = _list.get(position)
            val q = item.get("q")
            q?.let{
                val urlFull = "$WEATHERINFO_URL&q=$q&appid=$APP_ID"
                receiveWeatherInfo(urlFull)
            }
        }
    }

}