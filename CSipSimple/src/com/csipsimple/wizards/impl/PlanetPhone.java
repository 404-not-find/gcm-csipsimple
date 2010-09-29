/**
 * Copyright (C) 2010 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.csipsimple.wizards.impl;

import java.util.Locale;

import android.preference.EditTextPreference;
import android.text.InputType;

import com.csipsimple.R;
import com.csipsimple.utils.PreferencesWrapper;
import com.csipsimple.wizards.SimplePrefsWizard;
import com.csipsimple.wizards.WizardUtils.WizardInfo;

public class PlanetPhone extends SimplePrefsWizard {
	
	public static WizardInfo getWizardInfo() {
		WizardInfo result = new WizardInfo();
		result.id =  "PLANETPHONE";
		result.label = "PlanetPhone";
		result.icon = R.drawable.ic_wizard_planetphone;
		result.priority = 10;
		result.countries = new Locale[]{
			new Locale("BG", "bg"),
		};
		return result;
	}

	@Override
	protected String getDomain() {
		return "sip.planetphone.net";
	}
	
	@Override
	protected String getDefaultName() {
		return getWizardInfo().label;
	}

	@Override
	protected String getWizardId() {
		return getWizardInfo().id;
	}
	
	//Customization
	protected void fillLayout() {
		super.fillLayout();
		EditTextPreference phoneNumber = ((EditTextPreference) findPreference("phone_number"));
		phoneNumber.setTitle(R.string.w_common_username);
		phoneNumber.setDialogTitle(R.string.w_common_username);
		phoneNumber.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
	}
	
	protected void buildAccount() {
		super.buildAccount();
		if(account.id == null || account.id < 0) {
			//First run, we should set settings !
			PreferencesWrapper p = new PreferencesWrapper(this);
			p.setPreferenceBooleanValue(PreferencesWrapper.ENABLE_VAD, true);
		}
	}
}
