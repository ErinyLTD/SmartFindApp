/*
 * SmartFind - Find your phone with an SMS from a trusted contact
 * Copyright (C) 2026 ErinyLTD
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.smartfind.app

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.smartfind.app.testing.robolectric.RobolectricTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import com.smartfind.app.util.PhoneNumberHelper

@RobolectricTest
class MainActivityContactsSpec : BehaviorSpec({

    lateinit var app: Application

    beforeEach {
        app = ApplicationProvider.getApplicationContext()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    // ==========================================
    // URI Validation — ensure Android contacts, not SIM
    // ==========================================

    Given("URI Validation") {

        When("Phone CONTENT_URI is examined") {
            Then("it uses com.android.contacts authority") {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                uri.authority shouldBe "com.android.contacts"
            }

            Then("path ends with /data/phones") {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                (uri.path?.endsWith("/data/phones") == true).shouldBeTrue()
            }

            Then("does not reference SIM icc") {
                val uriStr = ContactsContract.CommonDataKinds.Phone.CONTENT_URI.toString()
                uriStr.contains("icc").shouldBeFalse()
                uriStr.contains("content://sim").shouldBeFalse()
            }
        }

        When("Contacts CONTENT_URI is compared to Phone CONTENT_URI") {
            Then("they are separate URIs") {
                ContactsContract.Contacts.CONTENT_URI shouldNotBe
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            }
        }
    }

    // ==========================================
    // Column validation — projection used by loadDeviceContacts()
    // ==========================================

    Given("Column validation") {

        When("DISPLAY_NAME column constant is checked") {
            Then("it is 'display_name'") {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME shouldBe "display_name"
            }
        }

        When("NUMBER column constant is checked") {
            Then("it is 'data1'") {
                ContactsContract.CommonDataKinds.Phone.NUMBER shouldBe "data1"
            }
        }

        When("TYPE column constant is checked") {
            Then("it is 'data2'") {
                ContactsContract.CommonDataKinds.Phone.TYPE shouldBe "data2"
            }
        }

        When("LABEL column constant is checked") {
            Then("it is 'data3'") {
                ContactsContract.CommonDataKinds.Phone.LABEL shouldBe "data3"
            }
        }
    }

    // ==========================================
    // Phone type constants
    // ==========================================

    Given("Phone type constants") {

        When("TYPE_MOBILE is checked") {
            Then("it is 2") {
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE shouldBe 2
            }
        }

        When("TYPE_HOME is checked") {
            Then("it is 1") {
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME shouldBe 1
            }
        }

        When("TYPE_WORK is checked") {
            Then("it is 3") {
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK shouldBe 3
            }
        }
    }

    // ==========================================
    // RawContacts insert (Robolectric shadow supports this)
    // ==========================================

    Given("RawContacts insert") {

        When("a raw contact is inserted via RawContacts URI") {
            Then("insert succeeds with a valid ID") {
                val rawUri = app.contentResolver.insert(
                    ContactsContract.RawContacts.CONTENT_URI,
                    ContentValues().apply {
                        put(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.google")
                        put(ContactsContract.RawContacts.ACCOUNT_NAME, "test@gmail.com")
                    }
                )

                rawUri.shouldNotBeNull()
                (ContentUris.parseId(rawUri) > 0).shouldBeTrue()
            }
        }

        When("multiple raw contacts are inserted") {
            Then("all have unique IDs") {
                val ids = mutableListOf<Long>()
                repeat(5) {
                    val rawUri = app.contentResolver.insert(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContentValues()
                    )
                    rawUri.shouldNotBeNull()
                    ids.add(ContentUris.parseId(rawUri))
                }

                ids.toSet() shouldHaveSize 5
            }
        }

        When("two raw contacts are inserted sequentially") {
            Then("IDs are sequential") {
                val id1 = ContentUris.parseId(
                    app.contentResolver.insert(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContentValues()
                    )!!
                )
                val id2 = ContentUris.parseId(
                    app.contentResolver.insert(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContentValues()
                    )!!
                )
                (id2 > id1).shouldBeTrue()
            }
        }
    }

    // ==========================================
    // Permission verification
    // ==========================================

    Given("Permission verification") {

        When("READ_CONTACTS permission string is checked") {
            Then("it is the correct string") {
                Manifest.permission.READ_CONTACTS shouldBe "android.permission.READ_CONTACTS"
            }
        }
    }

    // ==========================================
    // MIME type constants for Data table
    // ==========================================

    Given("MIME type constants") {

        When("Phone CONTENT_ITEM_TYPE is checked") {
            Then("it is the correct MIME type") {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE shouldBe
                    "vnd.android.cursor.item/phone_v2"
            }
        }

        When("StructuredName CONTENT_ITEM_TYPE is checked") {
            Then("it is the correct MIME type") {
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE shouldBe
                    "vnd.android.cursor.item/name"
            }
        }
    }

    // ==========================================
    // Integration with PhoneNumberHelper
    // ==========================================

    Given("Integration with PhoneNumberHelper") {

        When("contact number from provider matches incoming SMS") {
            Then("numbersMatch returns true") {
                val contactNumber = "+44 7834 120 123"
                val incomingSms = "+447834120123"

                PhoneNumberHelper.numbersMatch(contactNumber, incomingSms).shouldBeTrue()
            }
        }

        When("local contact number is matched after normalization") {
            Then("numbersMatch returns true") {
                val contactNumber = "07834120123"
                val incomingSms = "+447834120123"

                PhoneNumberHelper.numbersMatch(contactNumber, incomingSms).shouldBeTrue()
            }
        }

        When("US formatted contact number is matched") {
            Then("numbersMatch returns true") {
                val contactNumber = "(555) 123-4567"
                val incomingSms = "+15551234567"

                PhoneNumberHelper.numbersMatch(contactNumber, incomingSms).shouldBeTrue()
            }
        }

        When("contact number from wrong person is compared") {
            Then("numbersMatch returns false") {
                val contactNumber = "+447834120123"
                val incomingSms = "+447999888777"

                PhoneNumberHelper.numbersMatch(contactNumber, incomingSms).shouldBeFalse()
            }
        }
    }
})
