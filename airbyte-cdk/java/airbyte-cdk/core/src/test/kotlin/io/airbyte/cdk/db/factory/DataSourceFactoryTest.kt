/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.cdk.db.factory

import com.zaxxer.hikari.HikariDataSource
import io.airbyte.cdk.integrations.JdbcConnector
import java.util.Map
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.testcontainers.containers.MySQLContainer

/** Test suite for the [DataSourceFactory] class. */
internal class DataSourceFactoryTest : CommonFactoryTest() {
    @Test
    fun testCreatingDataSourceWithConnectionTimeoutSetAboveDefault() {
        val connectionProperties = Map.of(CONNECT_TIMEOUT, "61")
        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                driverClassName,
                jdbcUrl,
                connectionProperties,
                JdbcConnector.getConnectionTimeout(connectionProperties, driverClassName)
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            61000,
            (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
        )
    }

    @Test
    fun testCreatingPostgresDataSourceWithConnectionTimeoutSetBelowDefault() {
        val connectionProperties = Map.of(CONNECT_TIMEOUT, "30")
        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                driverClassName,
                jdbcUrl,
                connectionProperties,
                JdbcConnector.getConnectionTimeout(connectionProperties, driverClassName)
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            30000,
            (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
        )
    }

    @Test
    fun testCreatingMySQLDataSourceWithConnectionTimeoutSetBelowDefault() {
        MySQLContainer<SELF>("mysql:8.0").use { mySQLContainer ->
            mySQLContainer.start()
            val connectionProperties = Map.of(CONNECT_TIMEOUT, "5000")
            val dataSource =
                DataSourceFactory.create(
                    mySQLContainer.getUsername(),
                    mySQLContainer.getPassword(),
                    mySQLContainer.getDriverClassName(),
                    mySQLContainer.getJdbcUrl(),
                    connectionProperties,
                    JdbcConnector.getConnectionTimeout(
                        connectionProperties,
                        mySQLContainer.getDriverClassName()
                    )
                )
            Assertions.assertNotNull(dataSource)
            Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
            Assertions.assertEquals(
                5000,
                (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
            )
        }
    }

    @Test
    fun testCreatingDataSourceWithConnectionTimeoutSetWithZero() {
        val connectionProperties = Map.of(CONNECT_TIMEOUT, "0")
        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                driverClassName,
                jdbcUrl,
                connectionProperties,
                JdbcConnector.getConnectionTimeout(connectionProperties, driverClassName)
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            Int.MAX_VALUE,
            (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
        )
    }

    @Test
    fun testCreatingPostgresDataSourceWithConnectionTimeoutNotSet() {
        val connectionProperties = Map.of<String, String>()
        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                driverClassName,
                jdbcUrl,
                connectionProperties,
                JdbcConnector.getConnectionTimeout(connectionProperties, driverClassName)
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10000,
            (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
        )
    }

    @Test
    fun testCreatingMySQLDataSourceWithConnectionTimeoutNotSet() {
        MySQLContainer<SELF>("mysql:8.0").use { mySQLContainer ->
            mySQLContainer.start()
            val connectionProperties = Map.of<String, String>()
            val dataSource =
                DataSourceFactory.create(
                    mySQLContainer.getUsername(),
                    mySQLContainer.getPassword(),
                    mySQLContainer.getDriverClassName(),
                    mySQLContainer.getJdbcUrl(),
                    connectionProperties,
                    JdbcConnector.getConnectionTimeout(
                        connectionProperties,
                        mySQLContainer.getDriverClassName()
                    )
                )
            Assertions.assertNotNull(dataSource)
            Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
            Assertions.assertEquals(
                60000,
                (dataSource as HikariDataSource).hikariConfigMXBean.connectionTimeout
            )
        }
    }

    @Test
    fun testCreatingADataSourceWithJdbcUrl() {
        val dataSource = DataSourceFactory.create(username, password, driverClassName, jdbcUrl)
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10,
            (dataSource as HikariDataSource).hikariConfigMXBean.maximumPoolSize
        )
    }

    @Test
    fun testCreatingADataSourceWithJdbcUrlAndConnectionProperties() {
        val connectionProperties = Map.of("foo", "bar")

        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                driverClassName,
                jdbcUrl,
                connectionProperties,
                JdbcConnector.getConnectionTimeout(connectionProperties, driverClassName)
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10,
            (dataSource as HikariDataSource).hikariConfigMXBean.maximumPoolSize
        )
    }

    @Test
    fun testCreatingADataSourceWithHostAndPort() {
        val dataSource =
            DataSourceFactory.create(username, password, host, port!!, database, driverClassName)
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10,
            (dataSource as HikariDataSource).hikariConfigMXBean.maximumPoolSize
        )
    }

    @Test
    fun testCreatingADataSourceWithHostPortAndConnectionProperties() {
        val connectionProperties = Map.of("foo", "bar")

        val dataSource =
            DataSourceFactory.create(
                username,
                password,
                host,
                port!!,
                database,
                driverClassName,
                connectionProperties
            )
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10,
            (dataSource as HikariDataSource).hikariConfigMXBean.maximumPoolSize
        )
    }

    @Test
    fun testCreatingAnInvalidDataSourceWithHostAndPort() {
        val driverClassName = "Unknown"

        Assertions.assertThrows(RuntimeException::class.java) {
            DataSourceFactory.create(username, password, host, port!!, database, driverClassName)
        }
    }

    @Test
    fun testCreatingAPostgresqlDataSource() {
        val dataSource =
            DataSourceFactory.createPostgres(username, password, host, port!!, database)
        Assertions.assertNotNull(dataSource)
        Assertions.assertEquals(HikariDataSource::class.java, dataSource.javaClass)
        Assertions.assertEquals(
            10,
            (dataSource as HikariDataSource).hikariConfigMXBean.maximumPoolSize
        )
    }

    @Test
    fun testClosingADataSource() {
        val dataSource1 = Mockito.mock(HikariDataSource::class.java)
        Assertions.assertDoesNotThrow { DataSourceFactory.close(dataSource1) }
        Mockito.verify(dataSource1, Mockito.times(1)).close()

        val dataSource2 = Mockito.mock(DataSource::class.java)
        Assertions.assertDoesNotThrow { DataSourceFactory.close(dataSource2) }

        Assertions.assertDoesNotThrow { DataSourceFactory.close(null) }
    }

    companion object {
        private const val CONNECT_TIMEOUT = "connectTimeout"

        var database: String? = null
        var driverClassName: String? = null
        var host: String? = null
        var jdbcUrl: String? = null
        var password: String? = null
        var port: Int? = null
        var username: String? = null

        @BeforeAll
        fun setup() {
            host = CommonFactoryTest.Companion.container!!.getHost()
            port = CommonFactoryTest.Companion.container!!.getFirstMappedPort()
            database = CommonFactoryTest.Companion.container!!.getDatabaseName()
            username = CommonFactoryTest.Companion.container!!.getUsername()
            password = CommonFactoryTest.Companion.container!!.getPassword()
            driverClassName = CommonFactoryTest.Companion.container!!.getDriverClassName()
            jdbcUrl = CommonFactoryTest.Companion.container!!.getJdbcUrl()
        }
    }
}
