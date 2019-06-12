package com.example

import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.NonFatal

object SlickBatchingDemo {
  val log: Logger = LoggerFactory.getLogger("demo")

  final case class Foo(name: String, id: Int, description: String, quantity: Int)

  def main(args: Array[String]): Unit = {
    val db = Database.forConfig("example")

    try {
      val program =
        for {
          _ <- Setup.createTable
          (_, foos) <- Insert.randomBatch(100)
          _ <- Verify.allOf(foos)
          _ <- Setup.dropTable
        } yield ()

      Await.ready(db.run(program), 30.seconds)
    } catch {
      case NonFatal(_) => Await.ready(db.run(Setup.dropTable), 10.seconds)
    } finally {
      db.close()
    }
  }

  object Setup {
    def createTable: DBIO[Int] =
      sqlu"""create table foo(
        name text primary key,
        id int not null,
        description text,
        quantity int not null
      )"""

    def dropTable: DBIO[Int] = sqlu"""drop table foo"""
  }

  object Insert {
    val BatchSql = """INSERT INTO foo (name, id, description, quantity) VALUES (?, ?, ?, ?);"""

    def randomBatch(size: Int): DBIO[(List[Int], List[Foo])] = {
      if (size <= 0) {
        DBIO.successful(Nil -> Nil)
      } else {
        val foos = (0 until size).map { _ =>
          Foo(
            name = Random.alphanumeric.take(10).mkString(""),
            id = Random.nextInt(1000),
            description = Random.alphanumeric.take(32).mkString(""),
            quantity = Random.nextInt(1000)
          )
        }.toList

        batch(foos).map(result => result -> foos)
      }
    }

    def batch(foos: List[Foo]): DBIO[List[Int]] = {
      SimpleDBIO[List[Int]] { session =>
        val statement = session.connection.prepareStatement(BatchSql)

        foos.foreach { foo =>
          statement.setString(1, foo.name)
          statement.setInt(2, foo.id)
          statement.setString(3, foo.description)
          statement.setInt(4, foo.quantity)
          statement.addBatch()
        }

        statement.executeBatch().toList
      }.map { results =>
        log.info(s"Successfully inserted ${results.sum} records")
        results
      }
    }
  }

  object Verify {
    implicit val getFooResult: GetResult[Foo] = GetResult(r => Foo(r.<<, r.<<, r.<<, r.<<))

    def allOf(foos: List[Foo]): DBIO[List[Foo]] =
      DBIO.sequence(
        foos.map { foo =>
          sql"""select name, id, description, quantity from foo where name = ${foo.name}""".as[Foo].map(verify(foo, _))
        }
      ).map(_.flatten)

    private def verify(foo: Foo, potentialFoos: Vector[Foo]): Option[Foo] = {
      potentialFoos.headOption.flatMap { matchedFoo =>
        if (matchedFoo != foo) {
          log.warn(s"Error! Found foo $matchedFoo but wanted foo $foo")
          None
        } else {
          log.info(s"Verified foo $foo")
          Some(matchedFoo)
        }
      }
    }
  }
}
