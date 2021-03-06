package at.grahsl.kafka.connect.mongodb.processor;

import at.grahsl.kafka.connect.mongodb.MongoDbSinkConnectorConfig;
import at.grahsl.kafka.connect.mongodb.converter.SinkDocument;
import com.mongodb.DBCollection;
import org.apache.kafka.connect.sink.SinkRecord;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class WhitelistProjector extends FieldProjector {

    public WhitelistProjector(MongoDbSinkConnectorConfig config) {
        this(config, config.getFieldProjectionList());
    }

    public WhitelistProjector(MongoDbSinkConnectorConfig config,
                              Set<String> fields) {
        super(config);
        this.fields = fields;
    }

    @Override
    public void process(SinkDocument doc, SinkRecord orig) {

        if(config.isUsingWhitelistProjection()) {
            doc.getValueDoc().ifPresent(vd ->
                    doProjection("", vd)
            );
        }

        next.ifPresent(pp -> pp.process(doc,orig));
    }

    @Override
    void doProjection(String field, BsonDocument doc) {

        //special case short circuit check for '**' pattern
        //this is essentially the same as not using
        //whitelisting at all but instead take the full record
        if(fields.contains(FieldProjector.DOUBLE_WILDCARD)) {
            return;
        }

        Iterator<Map.Entry<String, BsonValue>> iter = doc.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<String, BsonValue> entry = iter.next();

            String key = field.isEmpty() ? entry.getKey()
                    : field + FieldProjector.SUB_FIELD_DOT_SEPARATOR + entry.getKey();
            BsonValue value = entry.getValue();

            if(!fields.contains(key)
                    //NOTE: always keep the _id field
                    && !key.equals(DBCollection.ID_FIELD_NAME)) {

                if(!checkForWildcardMatch(key))
                    iter.remove();

            }

            if(value.isDocument()) {
                //short circuit check to avoid recursion
                //if 'key.**' pattern exists
                String matchDoubleWildCard = key
                        + FieldProjector.SUB_FIELD_DOT_SEPARATOR
                        + FieldProjector.DOUBLE_WILDCARD;
                if(!fields.contains(matchDoubleWildCard)) {
                    doProjection(key, (BsonDocument)value);
                }
            }

        }
    }

    private boolean checkForWildcardMatch(String key) {

        String[] keyParts = key.split("\\"+FieldProjector.SUB_FIELD_DOT_SEPARATOR);
        String[] pattern = new String[keyParts.length];
        Arrays.fill(pattern,FieldProjector.SINGLE_WILDCARD);

        for(int c=(int)Math.pow(2, keyParts.length)-1;c >= 0;c--) {

            int mask = 0x1;
            for(int d = keyParts.length-1;d >= 0;d--) {
                if((c & mask) != 0x0) {
                    pattern[d] = keyParts[d];
                }
                mask <<= 1;
            }

            if(fields.contains(String.join(FieldProjector.SUB_FIELD_DOT_SEPARATOR,pattern)))
                return true;

            Arrays.fill(pattern,FieldProjector.SINGLE_WILDCARD);
        }

        return false;
    }
}
