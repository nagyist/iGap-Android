/*
* This is the source code of iGap for Android
* It is licensed under GNU AGPL v3.0
* You should have received a copy of the license in this archive (see LICENSE).
* Copyright © 2017 , iGap - www.iGap.net
* iGap Messenger | Free, Fast and Secure instant messaging application
* The idea of the RooyeKhat Media Company - www.RooyeKhat.co
* All rights reserved.
*/

package com.iGap.response;

import com.iGap.G;
import com.iGap.module.SUID;
import com.iGap.proto.ProtoGroupGetMemberList;
import com.iGap.realm.RealmMember;
import com.iGap.realm.RealmRoom;
import com.iGap.realm.RealmRoomFields;
import io.realm.Realm;
import io.realm.RealmList;

public class GroupGetMemberListResponse extends MessageHandler {

    public int actionId;
    public Object message;
    public String identity;

    public GroupGetMemberListResponse(int actionId, Object protoClass, String identity) {
        super(actionId, protoClass, identity);

        this.message = protoClass;
        this.actionId = actionId;
        this.identity = identity;
    }

    @Override
    public void handler() {
        super.handler();

        final ProtoGroupGetMemberList.GroupGetMemberListResponse.Builder builder = (ProtoGroupGetMemberList.GroupGetMemberListResponse.Builder) message;

        G.onGroupGetMemberList.onGroupGetMemberList(builder.getMemberList());


        final RealmList<RealmMember> newMembers = new RealmList<>();
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

                RealmRoom realmRoom = realm.where(RealmRoom.class).equalTo(RealmRoomFields.ID, Long.parseLong(identity)).findFirst();
                if (realmRoom != null) {

                    realmRoom.getGroupRoom().setParticipantsCountLabel(builder.getMemberCount() + "");
                    realmRoom.getGroupRoom().getMembers().deleteAllFromRealm();

                    for (ProtoGroupGetMemberList.GroupGetMemberListResponse.Member member : builder.getMemberList()) {
                        RealmMember realmMem = realm.createObject(RealmMember.class, SUID.id().get());
                        realmMem.setRole(member.getRole().toString());
                        realmMem.setPeerId(member.getUserId());
                        newMembers.add(realmMem);
                    }
                    realmRoom.getGroupRoom().setMembers(newMembers);
                }
            }
        });

        realm.close();
    }

    @Override
    public void timeOut() {
        super.timeOut();
    }

    @Override
    public void error() {
        super.error();
    }
}

