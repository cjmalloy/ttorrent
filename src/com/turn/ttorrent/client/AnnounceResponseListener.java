/** Copyright (C) 2011 Turn, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.turn.ttorrent.client;

import java.util.EventListener;
import java.util.List;
import java.util.Map;

import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.client.message.TrackerMessage;
import com.turn.ttorrent.common.Peer;

/**
 * EventListener interface for objects that want to receive tracker responses.
 * 
 * @author mpetazzoni
 */
public interface AnnounceResponseListener extends EventListener {

	public void handleAnnounceResponse(TrackerMessage message);
}
