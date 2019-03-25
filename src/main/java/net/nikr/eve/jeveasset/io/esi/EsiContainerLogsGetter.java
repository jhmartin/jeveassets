/*
 * Copyright 2009-2019 Contributors (see credits.txt)
 *
 * This file is part of jEveAssets.
 *
 * jEveAssets is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * jEveAssets is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jEveAssets; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package net.nikr.eve.jeveasset.io.esi;

import java.util.Date;
import java.util.List;
import net.nikr.eve.jeveasset.data.api.accounts.EsiOwner;
import net.nikr.eve.jeveasset.gui.dialogs.update.UpdateTask;
import net.troja.eve.esi.ApiException;
import net.troja.eve.esi.ApiResponse;
import net.troja.eve.esi.model.CorporationContainersLogsResponse;


public class EsiContainerLogsGetter extends AbstractEsiGetter {

	public EsiContainerLogsGetter(UpdateTask updateTask, EsiOwner owner) {
		super(updateTask, owner, false, owner.getContainerLogsNextUpdate(), TaskType.CONTAINER_LOGS);
	}

	@Override
	protected void update() throws ApiException {
		if (owner.isCharacter()) {
			return; //Corporation Ednpoint
		}
		List<CorporationContainersLogsResponse> response = updatePages(DEFAULT_RETRIES, new EsiPagesHandler<CorporationContainersLogsResponse>() {
			@Override
			public ApiResponse<List<CorporationContainersLogsResponse>> get(Integer page) throws ApiException {
				return getCorporationApiAuth().getCorporationsCorporationIdContainersLogsWithHttpInfo((int)owner.getOwnerID(), DATASOURCE, null, page, null);
			}
		});
		owner.setContainerLogs(EsiConverter.toContainersLogCorporation(response));
	}

	@Override
	protected boolean haveAccess() {
		if (owner.isCorporation()) {
			return owner.isContainerLogs();
		} else {
			return true; //Overwrite the default, so, we don't get errors
		}
	}

	@Override
	protected void setNextUpdate(Date date) {
		owner.setContainerLogsNextUpdate(date);
	}

}