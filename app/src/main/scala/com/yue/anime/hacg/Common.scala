package com.yue.anime.hacg

import java.security.MessageDigest
import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import java.util.concurrent.TimeUnit

import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.os.AsyncTask
import android.view.View
import android.widget.ProgressBar
import com.squareup.okhttp.{FormEncodingBuilder, OkHttpClient, Request}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.Random

object HAcg {
  val HOST = "hacg.be"
  val WEB = s"http://www.$HOST"
  val WORDPRESS = s"$WEB/wordpress"
}

object Common {
  implicit def viewTo[T <: View](view: View): T = view.asInstanceOf[T]

  implicit def viewClick(func: View => Unit): View.OnClickListener = new View.OnClickListener {
    override def onClick(view: View): Unit = func(view)
  }

  implicit def dialogClick(func: (DialogInterface, Int) => Unit): DialogInterface.OnClickListener = new DialogInterface.OnClickListener {
    override def onClick(dialog: DialogInterface, which: Int): Unit = func(dialog, which)
  }

  implicit def dialogDismiss(func: DialogInterface => Unit): DialogInterface.OnDismissListener = new OnDismissListener {
    override def onDismiss(dialog: DialogInterface): Unit = func(dialog)
  }

  implicit class StringUtil(s: String) {
    def isNullOrEmpty = s == null || s.isEmpty

    def isNonEmpty = !isNullOrEmpty
  }

  private val datefmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZZZZZ")

  implicit def string2date(str: String): Option[Date] = {
    try {
      return Option(datefmt.parse(str))
    } catch {
      case _: ParseException =>
    }
    None
  }

  implicit def date2long(date: Date): Long = date.getTime

  implicit def date2string(date: Date): String = datefmt.format(date)

  def using[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A =
    try {
      f(closeable)
    } finally {
      closeable.close()
    }

  implicit class ReduceMap[T](list: List[T]) {
    def reduceMap(func: T => List[T]): List[T] = {
      list match {
        case Nil => Nil
        case head :: tail => head :: func(head).reduceMap(func) ::: tail.reduceMap(func)
      }
    }
  }

  implicit class digest2string(s: String) {
    def md5 = MessageDigest.getInstance("MD5").digest(s.getBytes).map("%02X".format(_)).mkString

    def sha1 = MessageDigest.getInstance("SHA1").digest(s.getBytes).map("%02X".format(_)).mkString
  }

  private val img = List(".jpg", ".png", ".webp")

  implicit class httpex(url: String) {
    def isImg = img.exists(url.toLowerCase.endsWith)

    def httpGet = {
      try {
        val http = new OkHttpClient()
        http.setConnectTimeout(30, TimeUnit.SECONDS)
        val request = new Request.Builder().get().url(url).build()
        val response = http.newCall(request).execute()
        Option(response.body().string(), response.request().urlString())
      } catch {
        case _: Exception => None
      }
    }

    def httpPost(post: Map[String, String]) = {
      try {
        val http = new OkHttpClient()
        http.setConnectTimeout(30, TimeUnit.SECONDS)
        val data = (new FormEncodingBuilder /: post)((b, o) => b.add(o._1, o._2)).build()
        val request = new Request.Builder().url(url).post(data).build()
        val response = http.newCall(request).execute()
        Option(response.body().string(), response.request().urlString())
      } catch {
        case _: Exception => None
      }
    }
  }

  implicit class jsoupex(html: Option[(String, String)]) {
    def jsoup = html match {
      case Some(h) => Option(Jsoup.parse(h._1, h._2))
      case _ => None
    }

    def jsoup[T](f: Document => T): Option[T] = {
      html.jsoup match {
        case Some(h) => Option(f(h))
        case _ => None
      }
    }
  }

  trait ViewEx[T, V <: View] {
    var _value: T = _

    def value = _value

    def value_=(v: T) = {
      _value = v
      _refresh()
    }

    var _view: V = _

    def view = _view

    def view_=(v: V) = {
      _view = v
      _refresh()
    }

    private def _refresh(): Unit = {
      if (view == null)
        return
      value match {
        case v: AnyRef if v != null =>
          refresh()
        case _ =>
      }
    }

    def refresh(): Unit
  }

  trait Busy extends ViewEx[Boolean, ProgressBar] {

    def busy = value

    def busy_=(b: Boolean): Unit = {
      value = b
    }

    def progress = view

    def progress_=(p: ProgressBar): Unit = {
      view = p
    }

    override def refresh(): Unit = {
      if (view != null) {
        view.setVisibility(if (busy) View.VISIBLE else View.INVISIBLE)
        view.setIndeterminate(busy)
      }
    }
  }

  val random = new Random(System.currentTimeMillis())

  def randomColor(alpha: Int = 0xFF) = android.graphics.Color.HSVToColor(alpha, Array[Float](random.nextInt(360), 1, 0.5F))
}

abstract class ScalaTask[A, P, R] extends AsyncTask[A, P, R] {
  final override def doInBackground(params: A*): R = background(params: _*)

  final override def onPreExecute(): Unit = super.onPreExecute()

  final override def onPostExecute(result: R): Unit = post(result)

  final override def onProgressUpdate(values: P*): Unit = progress(values: _*)

  def pre(): Unit = {}

  def post(result: R): Unit = {}

  def progress(values: P*): Unit = {}

  def background(params: A*): R
}
