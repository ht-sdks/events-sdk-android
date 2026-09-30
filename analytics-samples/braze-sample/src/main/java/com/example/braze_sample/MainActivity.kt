package com.example.braze_sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID
import com.example.braze_sample.databinding.ActivityMainBinding
import com.hightouch.analytics.Analytics
import com.hightouch.analytics.Options
import com.hightouch.analytics.Properties
import com.hightouch.analytics.Traits
import com.hightouch.analytics.ValueMap

class MainActivity : AppCompatActivity() {
    private val names = listOf("Jane", "Bob", "Ada", "Maya", "Luis", "Priya", "Omar", "Chen")
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        rollUser()

        binding.newUser.setOnClickListener {
            rollUser()
            binding.lastAction.text = getString(R.string.new_user)
        }
        binding.identifyA.setOnClickListener {
            runAction(getString(R.string.identify_a)) {
                analytics().identify(userId(), traits("pro"), null)
            }
        }
        binding.identifyAAgain.setOnClickListener {
            runAction(getString(R.string.identify_a_again)) {
                analytics().identify(userId(), traits("pro"), null)
            }
        }
        binding.changePlan.setOnClickListener {
            runAction(getString(R.string.change_plan)) {
                analytics().identify(userId(), traits("enterprise"), null)
            }
        }
        binding.customEvent.setOnClickListener {
            runAction(getString(R.string.custom_event)) {
                analytics().track("Class Booked", Properties().putValue("class_type", "Yoga"))
            }
        }
        binding.purchaseTwoProducts.setOnClickListener {
            runAction(getString(R.string.purchase_two_products)) {
                analytics()
                    .track(
                        "Order Completed",
                        Properties()
                            .putValue("order_id", "order-123")
                            .putRevenue(42.0)
                            .putTax(3.0)
                            .putCurrency("USD")
                            .putValue(
                                "products",
                                listOf(
                                    product(
                                        "RB-100",
                                        "Resistance Band",
                                        15,
                                        2,
                                        "Equinox",
                                        "Gear",
                                    ),
                                    product("MB-200", "Mat", 12, 1, "Equinox", "Gear"),
                                ),
                            ),
                    )
            }
        }
        binding.purchaseCustomName.setOnClickListener {
            runAction(getString(R.string.purchase_custom_name)) {
                analytics()
                    .track(
                        "Membership Purchased",
                        Properties()
                            .putValue("order_id", "order-456")
                            .putRevenue(99.0)
                            .putCurrency("USD")
                            .putValue(
                                "products",
                                listOf(product("MEM-1", "Monthly Membership", 99, 1)),
                            ),
                    )
            }
        }
        binding.screen.setOnClickListener {
            runAction(getString(R.string.screen)) { analytics().screen("Schedule") }
        }
        binding.optOutEvent.setOnClickListener {
            runAction(getString(R.string.opt_out_event)) {
                analytics()
                    .track(
                        "Private Event",
                        null,
                        Options().setIntegration("Appboy", false),
                    )
            }
        }
        binding.reset.setOnClickListener {
            runAction(getString(R.string.reset)) { analytics().reset() }
        }
    }

    private fun rollUser() {
        binding.userId.setText(UUID.randomUUID().toString())
        binding.firstName.setText(names.random())
    }

    private fun userId(): String = binding.userId.text.toString()

    private fun traits(plan: String): Traits {
        val firstName = binding.firstName.text.toString()
        val email = firstName.trim().lowercase().replace("\\s+".toRegex(), "") + "@example.com"
        return Traits()
            .putEmail(email)
            .putFirstName(firstName)
            .putGender("male")
            .putValue("plan", plan)
            .putAddress(Traits.Address().putCity("New York").putCountry("US"))
    }

    private fun product(
        sku: String,
        name: String,
        price: Int,
        quantity: Int,
        brand: String? = null,
        category: String? = null,
    ): ValueMap {
        val product =
            ValueMap()
                .putValue("sku", sku)
                .putValue("name", name)
                .putValue("price", price)
                .putValue("quantity", quantity)
        if (brand != null) {
            product.putValue("brand", brand)
        }
        if (category != null) {
            product.putValue("category", category)
        }
        return product
    }

    private fun runAction(label: String, action: () -> Unit) {
        action()
        binding.lastAction.text = label
    }

    private fun analytics(): Analytics = Analytics.with(this)
}
